package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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
public class LessonChangeListener {

    final ILearningLessonService learningLessonService;

    /**
     * 监听订单支付或课程报名的消息
     * @param order 接收订单mq传来的参数
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.ORDER_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
            log.info("LessonChahngeListener 接受到了消息：");
            //健壮性判断
            if ( order.getUserId() == null
                    || order.getOrderId() == null
                    || CollUtils.isEmpty(order.getCourseIds())
                    //|| oreder.getCourseIds() == null
                    //|| oreder.getCourseIds().size() == 0
                    ){
                //消息为空则直接返回，抛出异常的话会触发重试机制
                log.error("接收到Mq消息有误，订单数据为空");
                return;
            }

            log.debug("监听到用户{}的订单{}，需要添加课程{}到课表中", order.getUserId(), order.getOrderId(), order.getCourseIds());
            learningLessonService.addUserLessons(order.getUserId(),order.getCourseIds());
    }


}
