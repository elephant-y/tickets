package com.ticket.order.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.order.entity.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
