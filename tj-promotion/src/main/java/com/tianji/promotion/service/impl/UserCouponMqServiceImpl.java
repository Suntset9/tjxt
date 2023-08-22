package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.config.PromotionConfig;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.discount.Discount;
import com.tianji.promotion.discount.DiscountStrategy;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.DiscountType;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponMqServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService exchangeCodeService;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitMqHelper rabbitMqHelper;
    private final ICouponScopeService couponScopeService;
    private final Executor discountSolutionExecutor;

    //领取优惠券  分布式锁 可以对优惠券加锁
    @MyLock(name = "lock:coupon:uid:#{id}")
    @Override
    //@Transactional
    public void receiveCoupon(Long id) {
        //1.根据id查询优惠券信息 做相关校验
        if (id == null){
            throw new BadRequestException("非法参数");
        }
        //Coupon coupon = couponMapper.selectById(id);
        //从redis中获取优惠信息
        Coupon coupon = queryCouponByCache(id);
        if (coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        //if (coupon.getStatus()!= CouponStatus.ISSUING){
        //    throw new BadRequestException("该优惠券状态不在发放中");
        //}
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) && now.isAfter(coupon.getIssueEndTime())){
            throw new BadRequestException("该优惠券已过期或未开始发放");
        }
        //if (coupon.getTotalNum()<=0 || coupon.getIssueNum() >= coupon.getTotalNum()){
        if (coupon.getTotalNum()<=0 ){
            throw new BadRequestException("该优惠券库存不足");
        }
        Long userId = UserContext.getUser();

        //获取当前用户 对该优惠券 已领数量  user_coupon 条件 userid  couponid  统计数量
        /*Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, id)
                .count();
        if (count != null && count >= coupon.getUserLimit()){
            throw new BadRequestException("已达到领取上限");
        }

        //2.优惠券的已发放数量+1
        //coupon.setIssueNum(coupon.getIssueNum()+1);
        //couponMapper.updateById(coupon);
        couponMapper.incrIssueNum(id);//采用这种方式  考虑并发控制 后期仍需修改

        //3.生成用户券
        saveUserCoupon(userId,coupon);*/

        //checkAndCreateUserCoupon(userId, coupon, null);
/*        synchronized (userId.toString().intern()) {
            //从aop上下文中 获取当前类的代理对象
            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
            //checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用原对象方法 默认带 this.
            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);//这种写法调用代理对象的方法，方法是由事务处理的
        }*/

/*        //生成锁的key  通过工具类实现分布式锁
        String key = "lock:coupon:uid:" + userId;
        //创建锁对象
        RedisLock lock = new RedisLock(key, redisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(5, TimeUnit.SECONDS);
        if (!isLock){//判断是否成功
            throw new BizIllegalException("请求太频繁了");
        }
        try {//获取锁成功 执行业务
            //从aop上下文中 获取当前类的代理对象
            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
            //checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用原对象方法 默认带 this.
            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);//这种写法调用代理对象的方法，方法是由事务处理的

        }finally {
            //释放锁
            lock.unlock();
        }*/


/*        //生成锁的key  通过Redisson实现分布式锁
        String key = "lock:coupon:uid:" + userId;
        //获取锁对象
        RLock lock = redissonClient.getLock(key);
        try {
            //获取锁  看门狗不能设置失效时间  采用默认的失效三十秒   否则看门狗会失效
            boolean isLock = lock.tryLock();
            if (!isLock){//判断是否成功
                throw new BizIllegalException("请求太频繁了");
            }
            //从aop上下文中 获取当前类的代理对象
            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
            //checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用原对象方法 默认带 this.
            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);//这种写法调用代理对象的方法，方法是由事务处理的

        }finally {
            //释放锁
            lock.unlock();
        }*/
        /**
         * 拆分步骤
         *       统计已领数量
         *       redisTemplate.opsForHash().get(key , userId);
         *       检验是否超过限领数量
         *       if(num >= coupon.getUserLimt)  throw xxxx
         *       修改已经数量+1
         *       long num = redisTemplate.opsforHash.put(key，userId，num+1)；
         */


        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + id;//prs:user:coupon:优惠券id
        //increment 代表本次领取后的 已领数量
        Long increment = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        if (increment > coupon.getUserLimit()){ //由于increment是+1后的结构，所以此处只能判断大于，不能等于
            throw new BizIllegalException("超出限领数量");
        }

        //修改优惠券的库存 -1
        String couponKey = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;
        redisTemplate.opsForHash().increment(couponKey,"totalNum",-1 );

        //发送消息到mq 消息的内容为  userId  couponId
        UserCouponDTO msg = new UserCouponDTO();
        msg.setCouponId(id);
        msg.setUserId(userId);
        rabbitMqHelper.send(
                MqConstants.Exchange.PROMOTION_EXCHANGE
                ,MqConstants.Key.COUPON_RECEIVE
                ,msg
        );

/*        //从aop上下文中 获取当前类的代理对象  因为是异步发送消息 所以不需要这个了 接收消息后调用
        IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
        //checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用原对象方法 默认带 this.
        userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);//这种写法调用代理对象的方法，方法是由事务处理的*/


    }

    //从redis中获取优惠券信息（领取开始和结束时间 发行总数量 限领数量）
    private Coupon queryCouponByCache(Long id) {
        //1.拼接key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + id;

        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        Coupon coupon = BeanUtils.mapToBean(entries,Coupon.class,false, CopyOptions.create());
        return coupon;
    }

    //@MyLock(name = "lock:coupon:uid:#{userId}",
    //        lockType = MyLockType.RE_ENTRANT_LOCK,
    //        lockStrategy = MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT) // TODO 这种方式疑似有bug 单人下单高并发会导致出现两次更新表操作
    //@MyLock(name = "lock:coupon:uid:#{userId}") //改为领券入口加锁
    @Transactional
    @Override
    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
        //Long类型 -128~127 之间是一个对象 超过该区间则是不同的对象
        //Long.toString 方法底层是new String 所以还是不同的对象
        //Long.toString.intern() intern方法是强制从常量池中取字符串
        //synchronized (userId.toString().intern()){
        // 1.获取当前用户 对该优惠券 已领数量  user_coupon 条件 userid  couponid  统计数量
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.1.校验限领数量
        if (count != null && count >= coupon.getUserLimit()){
            throw new BadRequestException("已达到领取上限");
        }

        //2.优惠券的已发放数量+1
        //coupon.setIssueNum(coupon.getIssueNum()+1);
        //couponMapper.updateById(coupon);
        int incrIssueNum = couponMapper.incrIssueNum(coupon.getId());//采用这种方式  考虑并发控制 后期仍需修改
        if (incrIssueNum == 0){
            throw new BizIllegalException("优惠券库存不足");
        }


        //3.生成用户券
        saveUserCoupon(userId,coupon);

        //4.更新兑换码的状态
        if (serialNum != null){
            exchangeCodeService.lambdaUpdate()
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .set(ExchangeCode::getUserId,userId)
                    .eq(ExchangeCode::getId,serialNum)//优惠券id
                    .update();
        }
        //}
        //throw  new RuntimeException("故意报错");
    }

    /**
     * 接收领券消息方法
     * @param msg
     */
    //@MyLock(name = "lock:coupon:uid:#{userId}")
    @Transactional
    @Override
    public void checkAndCreateUserCouponNew(UserCouponDTO msg) {
        //Long类型 -128~127 之间是一个对象 超过该区间则是不同的对象
        //Long.toString 方法底层是new String 所以还是不同的对象
        //Long.toString.intern() intern方法是强制从常量池中取字符串
        //synchronized (userId.toString().intern()){
        // 1.获取当前用户 对该优惠券 已领数量  user_coupon 条件 userid  couponid  统计数量
        //Integer count = this.lambdaQuery()
        //        .eq(UserCoupon::getUserId, userId)
        //        .eq(UserCoupon::getCouponId, coupon.getId())
        //        .count();
        //// 1.1.校验限领数量
        //if (count != null && count >= coupon.getUserLimit()){
        //    throw new BadRequestException("已达到领取上限");
        //}

        //1.从db中查询优惠券信息
        Coupon coupon = couponMapper.selectById(msg.getCouponId());
        if (coupon == null){
            return;
        }

        //2.优惠券的已发放数量+1
        //coupon.setIssueNum(coupon.getIssueNum()+1);
        //couponMapper.updateById(coupon);
        int incrIssueNum = couponMapper.incrIssueNum(coupon.getId());//采用这种方式  考虑并发控制 后期仍需修改
        if (incrIssueNum == 0){
            //throw new BizIllegalException("优惠券库存不足");
            return;
        }


        //3.生成用户券
        saveUserCoupon(msg.getUserId(), coupon);

        ////4.更新兑换码的状态
        //if (serialNum != null){
        //    exchangeCodeService.lambdaUpdate()
        //            .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
        //            .set(ExchangeCode::getUserId,userId)
        //            .eq(ExchangeCode::getId,serialNum)//优惠券id
        //            .update();
        //}
        //}
        //throw  new RuntimeException("故意报错");
    }

    private void saveUserCoupon(Long userId, Coupon coupon) {
        UserCoupon UserCoupon = new UserCoupon();
        UserCoupon.setUserId(userId);
        UserCoupon.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();//优惠券使用 开始时间
        LocalDateTime termEndTime = coupon.getTermEndTime();//优惠券使用 截止时间
        if (termEndTime == null || termBeginTime == null){//没传时间代表使用固定天数
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());//设置领取+天数为过期时间
        }
        UserCoupon.setTermBeginTime(termBeginTime);
        UserCoupon.setTermEndTime(termEndTime);
        this.save(UserCoupon);
    }


    @Override
    public void exchangeCoupon(String code) {
        //1.校验code是否为空
        if (StringUtils.isBlank(code)){
            throw new BadRequestException("非法参数");
        }
        //2.解析兑换码得到自增id
        long serialNum = CodeUtil.parseCode(code);
        //  校验是否已经兑换 SETBIT KEY 4 1 ，这里直接执行setbit，通过返回值来判断是否兑换过
        //3.判断兑换码是否已兑换  采用redis的Bitmap结构  setBit key offset 1  如果方法返回为true 代表兑换码已经兑换
        boolean result =  exchangeCodeService.updateExchangeCodeMark(serialNum,true);
        if ( result){
            //说明兑换码已经被兑换了
            throw new BizIllegalException("兑换码已被使用");
        }
        try {
            //4.判断兑换码是否存在 根据自增id查询 主键查询  查询兑换码对应的优惠券id
            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
            if (exchangeCode ==null){
                throw new BizIllegalException("兑换码不存在");
            }
            //5.判断是否过期
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredTime = exchangeCode.getExpiredTime();
            if (now.isAfter(expiredTime)){
                throw new BizIllegalException("兑换码已过期");
            }
            //校验并生成用户券
            Long userId = UserContext.getUser();
            //查询优惠券信息
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            if (coupon  == null){
                throw new BizIllegalException("优惠码不存在");
            }
            //校验并生成用户券，更新兑换码状态
            checkAndCreateUserCoupon(userId,coupon,serialNum);
        }catch (Exception e){
            //10.将兑换码的状态重  重置兑换的标记 0
            exchangeCodeService.updateExchangeCodeMark(serialNum,false);
            throw e;
        }

    }


    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
        //1.查询当前用户可用的优惠券 coupon和user_coupon表  条件 userId status=1  查哪些字段：优惠券的规则 优惠券id  用户券id
        List<Coupon> coupons = getBaseMapper().queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        log.debug("用户的优惠券共有 {}张",coupons.size());
        for (Coupon coupon : coupons) {
            log.debug("优惠券 {} , {}",DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);

        }


        //2.初筛
        //2.1计算订单的总金额  对coures的price累加  或采用 for循环++
        int totalAmout = courses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        log.debug("订单的总金额 {}", totalAmout);

        //2.2.检验优惠券是否可用
        /*ArrayList<Coupon> availiablecoupons = new ArrayList<>();
        for (Coupon coupon : coupons) {
            boolean flag = DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmout, coupon);
            if (flag){
                availiablecoupons.add(coupon);
            }
        }*/
        List<Coupon> availableCoupons = coupons.stream()
                .filter(coupon -> DiscountStrategy.getDiscount(coupon.getDiscountType()).canUse(totalAmout, coupon))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)){
            return CollUtils.emptyList();
        }
        log.debug("经过初筛后 还剩 {}张",availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("优惠券：{} , {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        //3.细筛（需要考虑优惠券的限定范围）排列组合
        Map<Coupon, List<OrderCourseDTO>> avaMap =  findAvailableCoupons(availableCoupons,courses);
        if (avaMap.isEmpty()){
            return CollUtils.emptyList();
        }
        Set<Map.Entry<Coupon, List<OrderCourseDTO>>> entries = avaMap.entrySet();
        for (Map.Entry<Coupon, List<OrderCourseDTO>> entry : entries) {
            log.debug("细筛之后优惠券 {}  {}",
                    DiscountStrategy.getDiscount(entry.getKey().getDiscountType()).getRule(entry.getKey()),
                    entry.getKey());
            List<OrderCourseDTO> value = entry.getValue();
            for (OrderCourseDTO courseDTO : value) {
                log.debug("可用课程 {}",courseDTO);
            }
        }
        availableCoupons = new ArrayList<>(avaMap.keySet());//才是真正可用的优惠券集合
        log.debug("经过细筛之后的 优惠券个数：{}",availableCoupons.size());
        for (Coupon coupon : availableCoupons) {
            log.debug("优惠券：{} , {}",
                    DiscountStrategy.getDiscount(coupon.getDiscountType()).getRule(coupon),
                    coupon);
        }
        //排列组合
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon availableCoupon : availableCoupons) {
            solutions.add(List.of(availableCoupon));//添加单券到方案中
        }
        log.debug("排列组合");
        for (List<Coupon> solution : solutions) {
            List<Long> cids = solution.stream().map(Coupon::getId).collect(Collectors.toList());
            log.debug("{}",cids);
        }

        //4.计算每一种组合的优惠明细
        /*log.debug("开始计算 每一种组合的优惠明细");
        List<CouponDiscountDTO> dtos = new ArrayList<>();
        for (List<Coupon> solution : solutions) {
            CouponDiscountDTO dto =  calculateSolutionDiscount(avaMap,courses,solution);// avaMap 优惠券和可用课程映射集合,coupons 订单中所有的课程,solution 方案
            log.debug("方案最终优惠 {} 方案中优惠券使用了 {} 规则{}",dto.getDiscountAmount(),dto.getIds(),dto.getRules());
            dtos.add(dto);
        }*/

        //5.使用多线程改造第四步 并行计算每一种组合的优惠明细
        log.debug("开始计算 每一种组合的优惠明细");
        //List<CouponDiscountDTO> dtos = new ArrayList<>();//线程不安全
        List<CouponDiscountDTO> dtos = Collections.synchronizedList(new ArrayList<>(solutions.size()));//线程安全
        CountDownLatch latch = new CountDownLatch(solutions.size());//计数器
        for (List<Coupon> solution : solutions) {
            CompletableFuture.supplyAsync(new Supplier<CouponDiscountDTO>() {
                @Override
                public CouponDiscountDTO get() {
                    log.debug("线程{}开始计算方案{}",
                            Thread.currentThread().getName(),
                            solution.stream().map(Coupon::getId).collect(Collectors.toList()));

                    CouponDiscountDTO dto = calculateSolutionDiscount(avaMap, courses, solution);
                    return dto;
                }
            },discountSolutionExecutor).thenAccept(new Consumer<CouponDiscountDTO>() {
                @Override
                public void accept(CouponDiscountDTO dto) {
                    log.debug("方案最终优惠 {} 方案中优惠券使用了 {} 规则{}",dto.getDiscountAmount(),dto.getIds(),dto.getRules());
                    dtos.add(dto);
                    latch.countDown();//计数器减一
                }
            });
        }
        try {
            latch.await(2, TimeUnit.SECONDS);//主线程最多阻塞两秒
        }catch (InterruptedException e){
            log.error("多线程计算组合优惠明细 报错了",e);
        }

        //6.筛选最优解
        return findBestSolution(dtos);
    }

    /**
     * 求最优解
     * - 第一个Map用来记录用券相同时，优惠金额最高的方案；
     * - 第二个Map用来记录优惠金额相同时，用券最少的方案。
     * @param solutions
     * @return
     */
    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> solutions) {
        //1.创建两个map 分别记录用券相同，金额最高     金额相同  用券最少
        HashMap<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();//券组合优惠方案
        HashMap<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();//优惠金额优惠方案

        //2.循环方案  向map中记录  用券相同， 金额最高     金额相同   用券最少
        for (CouponDiscountDTO solution : solutions) {
            //2.1 对优惠券id 升序，转字符串 然后以逗号拼接
            String ids = solution.getIds().stream().sorted(Comparator.comparing(Long::longValue)).map(String::valueOf).collect(Collectors.joining(","));
            //2.2 从moreDiscountMap中取  旧的记录  判断 旧的方案金额 大于等于 当前方案的优惠金额   当前方案忽略  处理下一个方案
            CouponDiscountDTO old = moreDiscountMap.get(ids);//旧的记录
            if (old != null && old.getDiscountAmount() >= solution.getDiscountAmount()){
                continue;
            }
            //2.3 从lessCouponMap中取 旧的记录 判断 如果 旧的方案用券数量  小于  当前方案用券数量 当前方案忽略  处理下一个方案
            old = lessCouponMap.get(solution.getDiscountAmount());
            //int size = old.getIds().size();//旧方案 用券数量
            int newSize = solution.getIds().size();//当前方案的用券数量
            if (old != null && newSize > 1 && old.getIds().size() <= newSize ){
                continue;
            }
            //2.4 添加更优方案到 map 中
            moreDiscountMap.put(ids,solution);//说明当前方案 更优
            lessCouponMap.put(solution.getDiscountAmount(),solution);//说明当前方案 更优
        }

        //3.求两个map的交集
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        //4.对最终方案结果  按优惠金额 倒序
        List<CouponDiscountDTO> latestBestSolution = bestSolution.stream()
                .sorted(Comparator.comparing(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
        return latestBestSolution;
    }

    /**
     * 计算每一个方案的 优惠信息
     * @param avaMap 优惠券和可用课程的映射集合
     * @param courses 订单中所有的课程
     * @param solution 方案
     * @return
     */
    private CouponDiscountDTO calculateSolutionDiscount(Map<Coupon, List<OrderCourseDTO>> avaMap,
                                                        List<OrderCourseDTO> courses,
                                                        List<Coupon> solution) {
        //1.创建方案结构dto对象
        CouponDiscountDTO dto = new CouponDiscountDTO();
        //2.初始化商品id和商品折扣明细的映射，初始折扣明细全都设置为0
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, orderCourseDTO -> 0));
        //3.计算该方案的优惠信息
        //3.1循环方案中优惠券
        for (Coupon coupon : solution) {
            //3.2取出该优惠券对应的可用课程
            List<OrderCourseDTO> availiableCourses = avaMap.get(coupon);
            //3.3计算可用课程的总金额（商品价格 - 该商品的折扣明细）
            int totalAmount = availiableCourses.stream()
                    .mapToInt(value -> value.getPrice() - detailMap.get(value.getId())).sum();
            //3.4判断优惠券是否可用
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount,coupon)){
                continue;//券不可用，跳出循环，继续处理下一个券
            }
            //3.5计算该优惠券使用后的折扣值
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            //3.6更新商品的折扣明细 （更新商品id的商品折扣明细） 更新到detailMap中
            calculateDetailDiscount(detailMap, availiableCourses, totalAmount, discountAmount);
            //3.7 累加每一个优惠券的优惠金额 赋值给方案结构dto对象  更新dto数据
            dto.getIds().add(coupon.getId());//只要执行当前这句话，就意味着 这个优惠券生效了
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());//不能覆盖 应该是所以生效的优惠券累加的结果

        }
        return dto;
    }

    /**
     * 计算商品 折扣明细
     * @param detailMap 商品id和商品的优惠明细 映射
     * @param availiableCourses 当前优惠券可用的课程集合
     * @param totalAmount 可用的课程的总金额
     * @param discountAmount 当前优惠券能优惠的金额
     */
    private void calculateDetailDiscount(Map<Long, Integer> detailMap,
                                         List<OrderCourseDTO> availiableCourses,
                                         int totalAmount,
                                         int discountAmount) {
        //目的：就是优惠券在使用后  计算每个商品的折扣明细
        //规则：前面的商品按比例计算, 最后一个商品折扣明细 = 总的优惠金额 - 前面商品优惠的总额
        int times = 0;//代表已处理商品个数
        int remainDiscount = discountAmount;//代表剩余的优惠金额
        for (OrderCourseDTO course : availiableCourses) {//循环可用商品
            times++;
            int discount = 0;//课程优惠的金额
            if (times == availiableCourses.size()){
                //说明是最后一个课程
                discount =  remainDiscount;
            }else {
                //是前面的课程 按比例
                discount = course.getPrice() * discountAmount / totalAmount;//此处先乘 在除 否则结果是0
                remainDiscount = remainDiscount - discount;
            }
            //将商品的折扣明细 添加到 detailMap 累加
            detailMap.put(course.getId(), discount + detailMap.get(course.getId()));
        }

    }

    /**
     * 细筛，查询每一个优惠券 对应的可用课程
     * @param coupons 初筛之后的优惠券集合
     * @param orderCourses 订单中的课程集合
     * @return
     */
    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupons(List<Coupon> coupons, List<OrderCourseDTO> orderCourses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>();
        //1.循环遍历初筛后的优惠券集合
        for (Coupon coupon : coupons) {
            //2.找出每一个优惠券的可用课程
            List<OrderCourseDTO> availableCourses = orderCourses;
            //2.1判断优惠券是否限定了范围 coupon.specific为true
            if (coupon.getSpecific()){
                //2.2查询限定范围  查询coupon_scope表，条件coupon_id
                List<CouponScope> scopeList = couponScopeService.lambdaQuery().eq(CouponScope::getCouponId, coupon.getId()).list();
                //2.3得到限定范围的id集合
                List<Long> scopeIds = scopeList.stream().map(CouponScope::getBizId).collect(Collectors.toList());
                //2.4从 orderCourses 订单中所有的课程集合  筛选 该范围内的课程
                availableCourses = orderCourses.stream()
                        .filter(orderCourseDTO -> scopeIds.contains(orderCourseDTO.getCateId()))
                        .collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourses)){
                continue;//说明当前优惠券限定了范围 但是在订单中的课程没有找到可用课程， 说明改卷不可以， 忽略改卷 进行下一个优惠券的处理
            }
            //3.计算该优惠券  可用课程的总金额
            int totalAmout = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();//代表可用课程的总金额
            //4.判断该优惠券是否可用  如果可用添加到map中
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmout,coupon)){
                map.put(coupon,availableCourses);//把优惠券id 和 可用课程集合添加
            }
        }
        return map;
    }
}
