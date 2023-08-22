package com.tianji.promotion.service.impl;

import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.N;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Async("generateExchangeCodeExecutor")//使用generateExchangeCodeExecutor 自定义线程池中的线程异步执行
    public void asyncGenerateExchangeCode(Coupon coupon) {
        log.debug("生成兑换码  线程名{}",Thread.currentThread().getName());
        //代表优惠卷的发放总数量，也就是需要生成的兑换码总数量
        Integer totalNum = coupon.getTotalNum();
        //方式1：循环兑换码总数量  循环中单个获取自增id  incr  （效率不高）
        //方式2：先调用 incrby 得到自增id最大值  然后再循环生成代码（只需要操作一次redis即可）
        //1. 先自增id   借助reids  incrby
        Long increment = redisTemplate.opsForValue().increment(PromotionConstants.COUPON_CODE_SERIAL_KEY,totalNum);
        if (increment == null){
            return;
        }
        int maxSerialNul = increment.intValue();//本地自增id的最大值  例： redis中为400 + 100(优惠卷数量) =  500  写入数据库后的值
        int begin = maxSerialNul - totalNum + 1;//自增id  循环开始值  例： 500 - 100 +1 = 401   401开始循环 》》循环到500

        //2.循环生成兑换码 调用工具类生成兑换码
        List<ExchangeCode> list = new ArrayList<>();
        for (int serialNum = begin; serialNum <= maxSerialNul; serialNum++) {
            String code = CodeUtil.generateCode(serialNum, coupon.getId());//参数1为自增id值 参数2为优惠卷id  内部会计算出0-15之间的数字，然后找对应的密钥
            ExchangeCode exchangeCode =  new ExchangeCode();
            exchangeCode.setId(serialNum);//兑换码id  ExchangeCode这个po类的主键生成策略需要修改为INPUT
            exchangeCode.setCode(code);
            exchangeCode.setExchangeTargetId(coupon.getId());//优惠卷id
            exchangeCode.setExpiredTime(coupon.getIssueEndTime());//兑换码 兑换的截止时间，就是优惠卷领取的截止时间
            list.add(exchangeCode);
        }
        //3.将兑换码信息保存db exchange_code 批量保存
        this.saveBatch(list);

        //4.写入redis缓存  member：couponId，score： 兑换码最大序列号 (非今日内容 day11内容)
        redisTemplate.opsForZSet().add(PromotionConstants.COUPON_RANGE_KEY,coupon.getId().toString(),maxSerialNul);

    }

    @Override
    public boolean updateExchangeCodeMark(long serialNum, boolean flag) {
        //拼接key
        String key = PromotionConstants.COUPON_CODE_MAP_KEY;
        //修改兑换码的 自增id 对应的offset值
        Boolean aboolean = redisTemplate.opsForValue().setBit(key, serialNum, flag);
        return aboolean != null && aboolean;
    }
}
