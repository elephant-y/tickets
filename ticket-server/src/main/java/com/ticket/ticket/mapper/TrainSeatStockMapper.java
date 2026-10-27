package com.ticket.ticket.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.dto.StockInfoResponse;
import com.ticket.ticket.entity.TrainSeatStock;
@Mapper 
public interface TrainSeatStockMapper extends BaseMapper<TrainSeatStock>{
    @Select("""
            SELECT s.seat_type_id, st.type_name AS seat_type_name,
                   s.from_station_no, s.to_station_no,
                   s.total_seat, s.sold_seat,
                   (s.total_seat - s.sold_seat) AS available
            FROM train_seat_stock s
            JOIN seat_type st ON st.id = s.seat_type_id
            WHERE s.train_id = #{trainId} AND s.train_date = #{date}
            ORDER BY s.from_station_no, s.to_station_no, s.seat_type_id
            """)
    List<StockInfoResponse> selectStockInfo(@Param("trainId") Long trainId,
                                            @Param("date") LocalDate date);
}
