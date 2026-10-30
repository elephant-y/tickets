package com.ticket.train.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.train.dto.TrainSearchResponse;
import com.ticket.train.entity.Train;
@Mapper 
public interface TrainMapper extends BaseMapper<Train>{
    @Select ("""
                SELECT t.id AS train_id, t.train_no, t.train_type,
                       s1.name AS start_station_name, s2.name AS end_station_name,
                       a.depart_time, b.arrive_time
                FROM train t
                JOIN station s1 ON s1.id = t.start_station_id
                JOIN station s2 ON s2.id = t.end_station_id
                JOIN train_station a ON a.train_id = t.id AND a.station_id =#{from}
                JOIN train_station b ON b.train_id = t.id AND b.station_id =#{to}
                WHERE a.station_no < b.station_no AND t.status = 1
                ORDER BY a.depart_time
                """)
    List<TrainSearchResponse> searchByStations(@Param ("from") Long from,@Param ("to") Long to);
}
