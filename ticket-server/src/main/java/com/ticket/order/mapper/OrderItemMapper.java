package com.ticket.order.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.order.entity.OrderItem;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
