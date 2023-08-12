package com.song;

import com.tianji.learning.LearningApplication;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest(classes = LearningApplication.class)
public class RedisBitMapTest {
    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    public void test(){
        //对test116 第三天 做签到
        //返结果代表offset为4  原来的值
        Boolean setBit = redisTemplate.opsForValue().setBit("test116", 2, true);
        System.out.println(setBit);
        if (setBit){
            //为ture代表已经有数据 已经签过到了
            //抛异常
        }
    }

    @Test
    public void test1(){
        //对test116 取第一到第三天的 签到记录 redis 的bitmap存的时二进制 取出来的时10进制
        //bitfield test116 get u3 0 取test116 从第一位开始取  取三位转换为无符号十进制
        List<Long> list = redisTemplate.opsForValue().bitField("test116", BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(3)).valueAt(0));
        //返回为集合实际里面只有一个参数
        Long aLong = list.get(0);
        System.out.println(aLong);

    }



}
