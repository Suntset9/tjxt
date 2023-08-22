package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author Sunset
 * @since 2023-08-13
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Api(tags = "优惠卷相关接口")
public class CouponController {

    private final ICouponService couponService;

    @ApiOperation("新增优惠卷接口--管理端")
    @PostMapping
    public void savaeCoupon(@RequestBody @Validated CouponFormDTO  dto){
        couponService.savaeCoupon(dto);
    }

    @ApiOperation("分页查询优惠券接口--管理端")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }

    @ApiOperation("发放优惠卷接口--管理端")
    @PutMapping("/{id}/issue")
    public void beginIssue(@PathVariable Long id, @RequestBody @Validated CouponIssueFormDTO dto){
        couponService.beginIssue(id,dto);
    }

    @ApiOperation("查询发放中的优惠卷--用户端")
    @GetMapping("/list")
    public List<CouponVO> queryIssuingCoupons(){
        return couponService.queryIssuingCoupons();
    }

}
