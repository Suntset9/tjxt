package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import jdk.jfr.RecordingState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ISignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public SignResultVO addSignRecords() {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.拼接key
        //SimpleDateFormat format1 = new SimpleDateFormat("yyyyMM");
        //format1.format(new Date());
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));//得到 :年月 格式化字符串
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        //3.利用Bitset命令 将签到记录保存到Redis的bitmap结构中 需要检验是否已签到
        int offset = now.getDayOfMonth() - 1;//计算offset
        //保存签到实际
        Boolean setBit = redisTemplate.opsForValue().setBit(key, offset, true);
        if (setBit){
            //说明已经签到了
            throw new BizIllegalException("不能重复签到");
        }

        //4。计算连续签到的天数
        int signDays =  countSignDays(key,now.getDayOfMonth());

        //5.计算连续签到 奖励积分
        int rewardPoints = 0;
        switch (signDays){
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
/*        if (signDays == 7){
            rewardPoints = 10;
        }else if (signDays == 14){
            rewardPoints =20;
        }else if (signDays == 28){
            rewardPoints = 40;
        }*/

        //  6.保存积分
        rabbitMqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId,rewardPoints+1));

        //7.封装vo返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    private int countSignDays(String key, int dayOfMonth) {
        //1.获取从本月开始第一填到今天所有的签到的数据， bitfiled  得到的是十进制
        // bitfield key get u天数 0
        List<Long> bitField = redisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0));
        if (CollUtils.isEmpty(bitField)){
            return 0;
        }

        Long num = bitField.get(0);
        log.debug("num {}",num);

        //2.num转二进制  从后往前推共有多少个1   与运算&  右移一位
        int count = 0; // 定义一个计数器
        // 循环，与1做与运算，得到最后一个bit，判断是否为0，为0则终止，为1则继续
        while ((num & 1) == 1){
            count++; // 计数器+1
            num = num>>>1;//把数字右移一位，最后一位被舍弃，倒数第二位成了最后一位
        }
        return count;
    }


    @Override
    public Byte[] querySignRecords() {
        //获取用户id
        Long userId = UserContext.getUser();
        //2.拼接key
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));//得到 :年月 格式化字符串
        //sign:uid:2:202305
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;

        //3.利用redis bitfield命令获取本月第一天到今天所有得签到记录
        int dayOfMonth = now.getDayOfMonth();
        //bitfield  key get u天数 0
        List<Long> bitField = redisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                .valueAt(0));

        if (CollUtils.isEmpty(bitField)){
            return new Byte[0];
        }

        Long num = bitField.get(0);

        int offset = dayOfMonth - 1;
        //4.利用与运算 和 位移  封装结果
        Byte[] arr = new Byte[dayOfMonth];

        while (offset >= 0){
            arr[offset] = (byte)(num & 1);//计算最后一天是否签到  赋值结果
            offset--;
            num = num >>>1;//右移
        }

        return arr;
    }
}
