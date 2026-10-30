package com.ticket.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.dto.OrderInfo;
import com.ticket.common.exception.BizException;
import com.ticket.common.result.Result;
import com.ticket.order.entity.Order;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.service.OrderService;

/**
 * 订单内部接口(pay-service 经 Feign 调用的「订单窗口」):
 *   查询订单(支付预检)/ 标记已支付(状态迁移)。
 * 状态迁移留在订单域:支付只是触发器,订单状态机是订单服务的职责。
 */
@RestController
@RequestMapping("/api/inner/order")
public class OrderInnerController {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderInnerController(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    /** 订单信息(pay-service 支付预检用:存在/归属/状态/金额) */
    @GetMapping("/{id}")
    public Result<OrderInfo> getOrder(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        return Result.success(new OrderInfo(order.getId(), order.getUserId(),
                order.getStatus(), order.getTotalAmount()));
    }

    /** 标记已支付(支付回调触发;条件更新幂等,重复回调无副作用) */
    @PostMapping("/payNotify")
    public Result<Boolean> payNotify(@RequestParam Long orderId) {
        return Result.success(orderService.markPaid(orderId));
    }
}
