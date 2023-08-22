package com.tianji.promotion.utils;

import com.tianji.common.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分布式锁AOP切面类 环绕增强    --AOP & 自定义注解定义切面与切入点增强方法
 * @author LENTz
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MyLockAspect implements Ordered {
    private final RedissonClient redissonClient;
    
    private final MyLockFactory lockFactory;
    
    
    @Around("@annotation(myLock)")
    public Object tryLock(ProceedingJoinPoint pjp, MyLock myLock) throws Throwable {
        // 1.基于SPEL表达式解析锁的 name
        String name = getLockName(myLock.name(), pjp);
        // 1`创建锁对象
        // RLock lock = redissonClient.getLock(myLock.name());  改进lockType用户基于自定义注解选择
        RLock lock = lockFactory.getLock(myLock.lockType(),name);
        // 枚举实现锁失败策略
        boolean isLock = myLock.lockStrategy().tryLock(lock, myLock);
        if (!isLock) {
            return null;
        }
        
        /*失败处理策略已经封装到lockstrategy中,无需再次处理
        // 2`尝试获取锁
        boolean isLock = lock.tryLock(myLock.waitTime(), myLock.leaseTime(), myLock.unit());
        // 3`判断是否成功
        if (!isLock) {
            // 3`1失败结束
            throw new BizIllegalException("请求太频繁");
        }*/
        try {
            // 3`2成功,执行业务
            return pjp.proceed();
        } finally {
            // 4`释放锁
            lock.unlock();
        }
    }
    
    /**
     * SPEL的正则规则
     */
    private static final Pattern pattern = Pattern.compile("\\#\\{([^\\}]*)\\}");
    /**
     * 方法参数解析器
     */
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    
    /**
     * 解析锁名称
     * @param name 原始锁名称
     * @param pjp 切入点
     * @return 解析后的锁名称
     */
    private String getLockName(String name, ProceedingJoinPoint pjp) {
        // 1.判断是否存在spel表达式
        if (StringUtils.isBlank(name) || !name.contains("#")) {
            // 不存在，直接返回
            return name;
        }
        // 2.构建context,也就是SPEL表达式获取参数的上下文环境，这里上下文就是切入点的参数列表
        EvaluationContext context = new MethodBasedEvaluationContext(
                TypedValue.NULL, resolveMethod(pjp), pjp.getArgs(), parameterNameDiscoverer);
        // 3.构建SPEL解析器
        ExpressionParser parser = new SpelExpressionParser();
        // 4.循环处理，因为表达式中可以包含多个表达式
        Matcher matcher = pattern.matcher(name);
        while (matcher.find()) {
            // 4.1.获取表达式
            String tmp = matcher.group();
            String group = matcher.group(1);
            // 4.2.这里要判断表达式是否以 T字符开头，这种属于解析静态方法，不走上下文
            Expression expression = parser.parseExpression(group.charAt(0) == 'T' ? group : "#" + group);
            // 4.3.解析出表达式对应的值
            Object value = expression.getValue(context);
            // 4.4.用值替换锁名称中的SPEL表达式
            name = name.replace(tmp, ObjectUtils.nullSafeToString(value));
        }
        return name;
    }
    
    private Method resolveMethod(ProceedingJoinPoint pjp) {
        // 1.获取方法签名
        MethodSignature signature = (MethodSignature)pjp.getSignature();
        // 2.获取字节码
        Class<?> clazz = pjp.getTarget().getClass();
        // 3.方法名称
        String name = signature.getName();
        // 4.方法参数列表
        Class<?>[] parameterTypes = signature.getMethod().getParameterTypes();
        return tryGetDeclaredMethod(clazz, name, parameterTypes);
    }
    
    private Method tryGetDeclaredMethod(Class<?> clazz, String name, Class<?> ... parameterTypes){
        try {
            // 5.反射获取方法
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                // 尝试从父类寻找
                return tryGetDeclaredMethod(superClass, name, parameterTypes);
            }
        }
        return null;
    }
    
    
    /**
     * Spring中的AOP切面有很多，会按照Order排序，按照Order值从小到大依次执行。Spring事务AOP的order值是Integer.MAX_VALUE，优先级最低。
     * 我们的分布式锁一定要先于事务执行，因此，我们的切面一定要实现Ordered接口，指定order值小于Integer.MAX_VALUE即可。
     * @return the order value
     * @author LENTz
     */
    @Override
    public int getOrder() {
        return 0;
    }
}