package com.tianji.promotion.service;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-13
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateExchangeCode(Coupon coupon);

    boolean updateExchangeCodeMark(long serialNum, boolean flag);
}
