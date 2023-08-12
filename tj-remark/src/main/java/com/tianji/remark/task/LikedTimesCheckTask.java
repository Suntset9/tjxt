package com.tianji.remark.task;


import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private static final List<String> BIZ_TYPES = List.of("QA","NOTE");//业务类型
    private static final int MAX_BIZ_SIZE = 30;//任务每次取的biz数量 防止一次性往mq发送消息

    private final ILikedRecordService recordService;


    //每20秒执行一次, 将redis中 业务类型 下面 某业务的点赞总数 发送消息到mq
    //@Scheduled(cron = "0/20 * * * * ?")
    @Scheduled(fixedDelay = 40000)//每隔40秒执行一次
    public void checkLikedTime(){
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMessage(bizType,MAX_BIZ_SIZE);
        }
    }




}
