package com.song;

import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.UserCouponStatus;
import io.lettuce.core.output.ScanOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class streamTest {


    @Test
    public void test() {
        List<UserCoupon> list = new ArrayList<>();
        UserCoupon c1 = new UserCoupon();
        c1.setUserId(1L);
        c1.setCouponId(101L);
        c1.setStatus(UserCouponStatus.USED);
        UserCoupon c2 = new UserCoupon();
        c2.setUserId(1L);
        c2.setCouponId(102L);
        c2.setStatus(UserCouponStatus.USED);
        UserCoupon c3 = new UserCoupon();
        c3.setUserId(1L);
        c3.setCouponId(101L);
        c3.setStatus(UserCouponStatus.UNUSED);
        list.add(c1);
        list.add(c2);
        list.add(c3);
        //统计userid为1的用户，每一个卷，已领数量
        Map<Long, Long> issueMap = new HashMap<>();//键: 优惠券id值:已领数量
        // 101  2
        // 102  1
        for (UserCoupon userCoupon : list) {
            Long num = issueMap.get(userCoupon.getCouponId());//优惠券领取数量
            if (num == null) {
                issueMap.put(userCoupon.getCouponId(), 1L);

            } else {
                issueMap.put(userCoupon.getCouponId(), Long.valueOf(num.intValue() + 1));
            }
        }
        System.out.println(issueMap);
        //stream  groupingBy 分组统计 Collectors.counting()
        Map<Long, Long> longMap = list.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        System.out.println(longMap) ;

        //stream  分组后过滤条件  未使用的
        Map<Long, Long> longMap1 = list.stream()
                .filter(c->c.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        System.out.println(longMap1) ;

    }

}
