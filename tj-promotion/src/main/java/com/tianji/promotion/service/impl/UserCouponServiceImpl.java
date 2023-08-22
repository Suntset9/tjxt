//package com.tianji.promotion.service.impl;
//
//import com.sun.jdi.NativeMethodException;
//import com.tianji.common.exceptions.BadRequestException;
//import com.tianji.common.exceptions.BizIllegalException;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.promotion.domain.po.Coupon;
//import com.tianji.promotion.domain.po.ExchangeCode;
//import com.tianji.promotion.domain.po.UserCoupon;
//import com.tianji.promotion.enums.CouponStatus;
//import com.tianji.promotion.enums.ExchangeCodeStatus;
//import com.tianji.promotion.mapper.CouponMapper;
//import com.tianji.promotion.mapper.UserCouponMapper;
//import com.tianji.promotion.service.IExchangeCodeService;
//import com.tianji.promotion.service.IUserCouponService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.tianji.promotion.utils.CodeUtil;
//import lombok.RequiredArgsConstructor;
//import org.springframework.aop.framework.AopContext;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//
///**
// * <p>
// * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
// * </p>
// *
// * @author Sunset
// * @since 2023-08-14
// */
//@Service
//@RequiredArgsConstructor
//public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
//
//    private final CouponMapper couponMapper;
//    private final IExchangeCodeService exchangeCodeService;
//
//    @Override
//    //@Transactional
//    public void receiveCoupon(Long id) {
//        //1.根据id查询优惠券信息 做相关校验
//        if (id == null){
//            throw new BadRequestException("非法参数");
//        }
//        Coupon coupon = couponMapper.selectById(id);
//        if (coupon == null){
//            throw new BadRequestException("优惠券不存在");
//        }
//        if (coupon.getStatus()!= CouponStatus.ISSUING){
//            throw new BadRequestException("该优惠券状态不在发放中");
//        }
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(coupon.getIssueBeginTime()) && now.isAfter(coupon.getIssueEndTime())){
//            throw new BadRequestException("该优惠券已过期或未开始发放");
//        }
//        if (coupon.getTotalNum()<=0 || coupon.getIssueNum() >= coupon.getTotalNum()){
//            throw new BadRequestException("该优惠券库存不足");
//        }
//        Long userId = UserContext.getUser();
//        //获取当前用户 对该优惠券 已领数量  user_coupon 条件 userid  couponid  统计数量
//        /*Integer count = this.lambdaQuery()
//                .eq(UserCoupon::getUserId, userId)
//                .eq(UserCoupon::getCouponId, id)
//                .count();
//        if (count != null && count >= coupon.getUserLimit()){
//            throw new BadRequestException("已达到领取上限");
//        }
//
//        //2.优惠券的已发放数量+1
//        //coupon.setIssueNum(coupon.getIssueNum()+1);
//        //couponMapper.updateById(coupon);
//        couponMapper.incrIssueNum(id);//采用这种方式  考虑并发控制 后期仍需修改
//
//        //3.生成用户券
//        saveUserCoupon(userId,coupon);*/
//
//        //checkAndCreateUserCoupon(userId, coupon, null);
//        synchronized (userId.toString().intern()) {
//            //从aop上下文中 获取当前类的代理对象
//            IUserCouponService userCouponServiceProxy = (IUserCouponService) AopContext.currentProxy();
//            //checkAndCreateUserCoupon(userId, coupon, null); //这种写法是调用原对象方法 默认带 this.
//            userCouponServiceProxy.checkAndCreateUserCoupon(userId, coupon, null);//这种写法调用代理对象的方法，方法是由事务处理的
//        }
//    }
//
//    @Transactional
//    @Override
//    public void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum) {
//        //Long类型 -128~127 之间是一个对象 超过该区间则是不同的对象
//        //Long.toString 方法底层是new String 所以还是不同的对象
//        //Long.toString.intern() intern方法是强制从常量池中取字符串
//        //synchronized (userId.toString().intern()){
//            // 1.获取当前用户 对该优惠券 已领数量  user_coupon 条件 userid  couponid  统计数量
//            Integer count = this.lambdaQuery()
//                    .eq(UserCoupon::getUserId, userId)
//                    .eq(UserCoupon::getCouponId, coupon.getId())
//                    .count();
//            // 1.1.校验限领数量
//            if (count != null && count >= coupon.getUserLimit()){
//                throw new BadRequestException("已达到领取上限");
//            }
//
//            //2.优惠券的已发放数量+1
//            //coupon.setIssueNum(coupon.getIssueNum()+1);
//            //couponMapper.updateById(coupon);
//            couponMapper.incrIssueNum(coupon.getId());//采用这种方式  考虑并发控制 后期仍需修改
//
//            //3.生成用户券
//            saveUserCoupon(userId,coupon);
//
//            //4.更新兑换码的状态
//            if (serialNum != null){
//                exchangeCodeService.lambdaUpdate()
//                        .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
//                        .set(ExchangeCode::getUserId,userId)
//                        .eq(ExchangeCode::getId,serialNum)//优惠券id
//                        .update();
//            }
//        //}
//        //throw  new RuntimeException("故意报错");
//    }
//
//    private void saveUserCoupon(Long userId, Coupon coupon) {
//        UserCoupon UserCoupon = new UserCoupon();
//        UserCoupon.setUserId(userId);
//        UserCoupon.setCouponId(coupon.getId());
//        LocalDateTime termBeginTime = coupon.getTermBeginTime();//优惠券使用 开始时间
//        LocalDateTime termEndTime = coupon.getTermEndTime();//优惠券使用 截止时间
//        if (termEndTime == null || termBeginTime == null){//没传时间代表使用固定天数
//            termBeginTime = LocalDateTime.now();
//            termEndTime = termBeginTime.plusDays(coupon.getTermDays());//设置领取+天数为过期时间
//        }
//        UserCoupon.setTermBeginTime(termBeginTime);
//        UserCoupon.setTermEndTime(termEndTime);
//        this.save(UserCoupon);
//    }
//
//
//    @Override
//    public void exchangeCoupon(String code) {
//        //1.校验code是否为空
//        if (StringUtils.isBlank(code)){
//            throw new BadRequestException("非法参数");
//        }
//        //2.解析兑换码得到自增id
//        long serialNum = CodeUtil.parseCode(code);
//        //  校验是否已经兑换 SETBIT KEY 4 1 ，这里直接执行setbit，通过返回值来判断是否兑换过
//        //3.判断兑换码是否已兑换  采用redis的Bitmap结构  setBit key offset 1  如果方法返回为true 代表兑换码已经兑换
//        boolean result =  exchangeCodeService.updateExchangeCodeMark(serialNum,true);
//        if ( result){
//            //说明兑换码已经被兑换了
//            throw new BizIllegalException("兑换码已被使用");
//        }
//        try {
//            //4.判断兑换码是否存在 根据自增id查询 主键查询  查询兑换码对应的优惠券id
//            ExchangeCode exchangeCode = exchangeCodeService.getById(serialNum);
//            if (exchangeCode ==null){
//                throw new BizIllegalException("兑换码不存在");
//            }
//            //5.判断是否过期
//            LocalDateTime now = LocalDateTime.now();
//            LocalDateTime expiredTime = exchangeCode.getExpiredTime();
//            if (now.isAfter(expiredTime)){
//                throw new BizIllegalException("兑换码已过期");
//            }
//            //校验并生成用户券
//            Long userId = UserContext.getUser();
//            //查询优惠券信息
//            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
//            if (coupon  == null){
//                throw new BizIllegalException("优惠码不存在");
//            }
//            //校验并生成用户券，更新兑换码状态
//            checkAndCreateUserCoupon(userId,coupon,serialNum);
//        }catch (Exception e){
//            //10.将兑换码的状态重  重置兑换的标记 0
//            exchangeCodeService.updateExchangeCodeMark(serialNum,false);
//            throw e;
//        }
//
//    }
//
//
//
//
//}
