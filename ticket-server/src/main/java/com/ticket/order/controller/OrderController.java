package com.ticket.order.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.common.result.Result;
import com.ticket.order.dto.CreateOrderRequest;
import com.ticket.order.service.OrderService;

@RestController 
@RequestMapping ("/api/order")
public class OrderController {
    private final OrderService orderService;
    public OrderController(OrderService orderService)
    {
        this.orderService=orderService;
    }
    @PostMapping("/create")
    public Result<?> create(@RequestBody CreateOrderRequest req,
                            @RequestHeader("X-User-Id") Long userId) {
        return Result.success(orderService.createOrder(req, userId));
    }
}
