package com.tianji.learning.mq;


import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LikeRecordListener {

    private final IInteractionReplyService replyService;

    /**
     * QA问答系统 消费者
     * @param list
     */
    @RabbitListener( bindings = @QueueBinding(
            value = @Queue(value = "qa.like.times.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    public void onMsg(List<LikedTimesDTO>  list){
        log.info("LukeRecordListener 监听到消息{}",list);
        //消息转po
        List<InteractionReply> replyList = new ArrayList<>();
        for (LikedTimesDTO dto : list) {
            InteractionReply reply = new InteractionReply();
            //将接收到的两个参数赋值
            reply.setId(dto.getBizId());
            reply.setLikedTimes(dto.getLikedTimes());
            replyList.add(reply);
        }
        //批量更新
        replyService.updateBatchById(replyList);
    }
/*    public void onMsg(LikedTimesDTO dto){
        log.info("LukeRecordListener 监听到消息{}",dto);
        InteractionReply reply = replyService.getById(dto.getBizId());
        if (reply == null){
            return;
        }
        reply.setLikedTimes(dto.getLikedTimes());
        replyService.updateById(reply);
    }*/



}
