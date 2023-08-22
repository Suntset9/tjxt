package com.tianji.api.client.promotion.fallback;

import com.tianji.api.client.promotion.PromotionCilent;
import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionCilent> {

    @Override
    public PromotionCilent create(Throwable cause) {
        log.error("远程调用promotion服务报错了",cause);
        return new PromotionCilent() {
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
                return null;
            }
        };
    }
}
