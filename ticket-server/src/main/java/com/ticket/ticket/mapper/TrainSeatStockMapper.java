package com.ticket.ticket.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * DB 兜底扣减(不超卖的第三重保障,订单事务内与 Redis 扣减同步执行)。
     * 单条 UPDATE 在行锁保护下原子执行 —— WHERE 里的「判断」和 SET 里的「扣」一体,不会被并发穿插。
     * 返回受影响行数:0 = 兜底条件(sold + count <= total)不满足 = 数据已漂移 → 调用方抛异常回滚。
     */
    @Update("""
            UPDATE train_seat_stock
            SET sold_seat = sold_seat + #{count}, version = version + 1
            WHERE train_id = #{trainId} AND train_date = #{trainDate}
              AND seat_type_id = #{seatTypeId}
              AND from_station_no = #{fromNo} AND to_station_no = #{toNo}
              AND sold_seat + #{count} <= total_seat
            """)
    int updateSold(@Param("trainId") Long trainId,
                   @Param("trainDate") LocalDate trainDate,
                   @Param("seatTypeId") Long seatTypeId,
                   @Param("fromNo") int fromNo,
                   @Param("toNo") int toNo,
                   @Param("count") int count);
}
