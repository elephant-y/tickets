package com.ticket.order.task;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.order.entity.Order;
import com.ticket.order.entity.SeatHold;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.mapper.SeatHoldMapper;
import com.ticket.order.service.OrderService;

@Component
public class OrderReconciliationTask {
    private static final Logger log = LoggerFactory.getLogger(OrderReconciliationTask.class);
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final SeatHoldMapper seatHoldMapper;
    public OrderReconciliationTask(OrderMapper orderMapper,OrderService orderService,SeatHoldMapper seatHoldMapper)
    {
        this.orderMapper=orderMapper;
        this.orderService=orderService;
        this.seatHoldMapper=seatHoldMapper;
    }
    @Scheduled(fixedDelayString = "${ticket.reconcile.interval-seconds:300}000")  // 见设计点1
    public void reconcile() {
        // ① 补刀:status=10 且创建超 30 分钟的订单(延迟消息丢失的兜底)
        List<Order> overdue = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, 10)
                        .lt(Order::getCreateTime, LocalDateTime.now().minusMinutes(30)));
        for (Order o : overdue) {
            try {
                orderService.cancelOrder(o.getId(), o.getUserId());   // 幂等,补刀安全
                log.info("对账补刀取消: {}", o.getId());
            } catch (Exception e) {
                log.error("对账取消失败,下轮重试: {}", o.getId(), e);
            }
        }
        List<Order> cancelled = orderMapper.selectList(
                new LambdaQueryWrapper<Order>().eq(Order::getStatus, 40));
        for (Order o : cancelled) {
            Long holdCnt = seatHoldMapper.selectCount(
                    new LambdaQueryWrapper<SeatHold>().eq(SeatHold::getOrderId, o.getId()));
            if (holdCnt > 0) {
                seatHoldMapper.delete(new LambdaQueryWrapper<SeatHold>()
                        .eq(SeatHold::getOrderId, o.getId()));
                log.info("对账补删凭证: {}", o.getId());
            }
        }
    }
}
