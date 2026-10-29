package com.ticket.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.common.result.Result;
import com.ticket.order.dto.CreateOrderRequest;
import com.ticket.order.entity.OrderRequest;
import com.ticket.order.mapper.OrderRequestMapper;
import com.ticket.order.service.OrderService;

@RestController 
@RequestMapping ("/api/order")
public class OrderController {
    private final OrderService orderService;
    private final OrderRequestMapper orderRequestMapper;
    public OrderController(OrderService orderService,OrderRequestMapper orderRequestMapper)
    {
        this.orderService=orderService;
        this.orderRequestMapper=orderRequestMapper;
    }
    @PostMapping("/create")
    public Result<?> create(@RequestBody CreateOrderRequest req,
                            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.createOrder(req, userId));
    }
    @GetMapping("/status")
    public Result<?> status(@RequestParam("requestId") String requestId) {
        OrderRequest record = orderRequestMapper.selectOne(
            new LambdaQueryWrapper<OrderRequest>()
                    .eq(OrderRequest::getRequestId, requestId));
    if (record == null) {
        throw new BizException(404, "排队记录不存在");
    }
    // 返回:status + orderId + failReason(0 排队中 / 1 成功 / 2 失败)
        return Result.success(record);   
    }
    @PostMapping("/cancel")
    public Result<?> cancel(@RequestParam("orderId") Long orderId,
                        @RequestHeader("X-User-Id") Long userId) {
        orderService.cancelOrder(orderId, userId);
        return Result.success();
    }

    /** 退票(20 已支付 → 50 已退票):座位回流与取消同链路;退款待支付模块 */
    @PostMapping("/refund")
    public Result<?> refund(@RequestParam("orderId") Long orderId,
                            @RequestHeader("X-User-Id") Long userId) {
        orderService.refundOrder(orderId, userId);
        return Result.success();
    }

}
