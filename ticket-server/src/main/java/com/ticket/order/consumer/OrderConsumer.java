package com.ticket.order.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ticket.order.dto.OrderMessage;
import com.ticket.order.entity.OrderRequest;
import com.ticket.order.mapper.OrderRequestMapper;
import com.ticket.order.service.OrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.rocketmq.spring.annotation.ConsumeMode;

@Component
@RocketMQMessageListener(
        topic = "ORDER_CREATE",
        consumerGroup = "ticket-order-consumer",
        consumeMode = ConsumeMode.ORDERLY)
public class OrderConsumer implements RocketMQListener<OrderMessage>{
    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRequestMapper orderRequestMapper;
    @Override
    public void onMessage(OrderMessage msg) {
        OrderRequest record = orderRequestMapper.selectOne(
                new LambdaQueryWrapper<OrderRequest>()
                        .eq(OrderRequest::getRequestId, msg.getRequestId()));
        if(record==null || record.getStatus()!=0)
        {
            log.info("消息重复投递或记录不存在,跳过: {}", msg.getRequestId());
            return;
        }
       try {
            Long orderId=orderService.executeOrder(msg.getReq(), msg.getUserId());
            orderRequestMapper.update(null, new LambdaUpdateWrapper<OrderRequest>()
                     .eq(OrderRequest::getRequestId, msg.getRequestId())
                     .set(OrderRequest::getStatus, 1)
                     .set(OrderRequest::getOrderId, orderId));
             log.info("下单处理成功: {} -> 订单 {}", msg.getRequestId(), orderId);
       } catch (Exception e) {
            String reason = e.getMessage() == null ? "未知错误" : e.getMessage();
            if (reason.length() > 190) reason = reason.substring(0, 190);   // 字段上限 200
            orderRequestMapper.update(null, new LambdaUpdateWrapper<OrderRequest>()
                    .eq(OrderRequest::getRequestId, msg.getRequestId())
                    .set(OrderRequest::getStatus, 2)
                    .set(OrderRequest::getFailReason, reason));
       }
    }
    
}
