package com.tianji.promotion.utils;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME) //作用:运行时生效
@Target(ElementType.METHOD) //作用在方法上
public @interface MyLock {
    String name(); //key

    long waitTime() default 1; //尝试加锁等待时间

    long leaseTime() default -1; //释放锁的时间

    TimeUnit unit() default TimeUnit.SECONDS;//时间单位

    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK; //代表类型  默认可重入

    MyLockStrategy lockStrategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT; //代表获取锁的失败策略

}