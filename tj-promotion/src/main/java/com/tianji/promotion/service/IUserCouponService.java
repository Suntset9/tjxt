package com.tianji.promotion.service;

import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-14
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long id);

    void checkAndCreateUserCoupon(Long userId, Coupon coupon, Long serialNum);

    void exchangeCoupon(String code);

    //接收领卷消息方法
    void checkAndCreateUserCouponNew(UserCouponDTO msg);

    //给tj—trade服务 远程调用使用的
    List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses);
}
