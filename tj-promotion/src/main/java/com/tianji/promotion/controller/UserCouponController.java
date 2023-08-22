package com.tianji.promotion.controller;


import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author Sunset
 * @since 2023-08-14
 */
@Api(tags = "用户优惠券相关接口")
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    private final IUserCouponService userCouponService;

    @ApiModelProperty("领取优惠券")
    @PostMapping("/{id}/receive")
    public void receiveCoupon(@PathVariable("id") Long id){
        userCouponService.receiveCoupon(id);
    }

    @PostMapping("/{code}/exchange")
    @ApiOperation("兑换码兑换优惠券")
    public void exchangeCoupon(@PathVariable("code") String code){
        userCouponService.exchangeCoupon(code);
    }

    /**
     * 给tj—trade服务 远程调用使用的
     * 查询可用优惠券方案
     * @param courses 订单中的课程信息
     * @return 方案集合
     */
    @ApiOperation("查询可用优惠券方案")
    @PostMapping("/available")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> courses){
        return userCouponService.findDiscountSolution(courses);
    }



}
