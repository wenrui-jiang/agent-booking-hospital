package com.atguigu.yygh.order.receiver;

import com.atguigu.common.rabbit.constant.MqConst;
import com.atguigu.yygh.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderReceiver {

    private static final Logger log = LoggerFactory.getLogger(OrderReceiver.class);

    @Autowired
    private OrderService orderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_8, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_8}
    ))
    public void patientTips(Message message) {
        try {
            int bodyLength = message == null || message.getBody() == null ? 0 : message.getBody().length;
            log.debug("Received patient tips task message, bodyLength={}", bodyLength);
            orderService.patientTips();
        } catch (Exception e) {
            log.error("Patient tips task failed; message will be acknowledged to avoid a retry storm", e);
        }
    }

}
