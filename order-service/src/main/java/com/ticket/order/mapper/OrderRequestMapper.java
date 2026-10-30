package com.ticket.order.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.order.entity.OrderRequest;

@Mapper
public interface OrderRequestMapper extends BaseMapper<OrderRequest> {
}
