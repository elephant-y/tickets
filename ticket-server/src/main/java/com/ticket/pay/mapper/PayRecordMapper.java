package com.ticket.pay.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.pay.entity.PayRecord;

@Mapper
public interface PayRecordMapper extends BaseMapper<PayRecord> {
}
