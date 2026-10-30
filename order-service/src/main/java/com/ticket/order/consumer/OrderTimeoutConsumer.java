package com.ticket.order.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.ticket.order.entity.Order;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.service.OrderService;

import org.apache.rocketmq.spring.annotation.ConsumeMode;

@Component
@RocketMQMessageListener(
        topic = "ORDER_TIMEOUT",
        consumerGroup = "ticket-timeout-consumer",   // 注意:独立消费组!
        consumeMode = ConsumeMode.CONCURRENTLY)      // 取消靠状态机幂等,不需要顺序;并发消费吞吐更高
public class OrderTimeoutConsumer implements RocketMQListener<String>{
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    public OrderTimeoutConsumer(OrderMapper orderMapper,OrderService orderService)
    {
        this.orderMapper=orderMapper;
        this.orderService=orderService;
    }
    @Override
    public void onMessage(String orderId) {
        Long id=Long.valueOf(orderId);
       Order order=orderMapper.selectById(id);
       if (order == null || order.getStatus() != 10) return;
       if(order==null || order.getStatus()!=10) return ;
       orderService.cancelOrder(id, order.getUserId());   // 系统替用户操作:传订单自己的 userId,归属校验通过
    }
    
}
