package com.tianji.learning.mq;


import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService recordService;

    /**
     * 签到增加的积分
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "sign.poins.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage message){
        log.debug("签到增加的积分   消费到消息   {} ",message);
        recordService.addPointsRecord(message, PointsRecordType.SIGN);
    }


    /**
     * 问答增加的积分
     * @param message
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.poins.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplynMessage(SignInMessage message){
        log.debug("问答增加的积分   消费到消息   {} ",message);
        recordService.addPointsRecord(message, PointsRecordType.QA);
    }


}
