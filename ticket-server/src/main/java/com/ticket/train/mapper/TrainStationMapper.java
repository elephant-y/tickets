package com.ticket.train.mapper;



import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.train.entity.TrainStation;
@Mapper 
public interface TrainStationMapper extends BaseMapper<TrainStation>{
    
}
