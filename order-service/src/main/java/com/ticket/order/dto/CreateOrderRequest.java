package com.ticket.order.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 下单请求:1~5 张票(多个乘车人),同一车次同一天同一座位类型同一区间。
 * 字段校验规则见 OrderService(票数 1~5、日期 ≥ 今天、乘客非空等)。
 */
public class CreateOrderRequest {

    /** 车次ID */
    private Long trainId;

    /** 乘车日期 */
    private LocalDate trainDate;

    /** 座位类型ID */
    private Long seatTypeId;

    /** 出发站ID(站序由服务端查经停表换算,不信任前端传站序) */
    private Long fromStationId;

    /** 到达站ID */
    private Long toStationId;

    /** 乘车人列表(1~5 人,票数 = 列表大小) */
    private List<Passenger> passengers;

    /** 乘车人 */
    public static class Passenger {
        /** 乘车人姓名 */
        private String name;
        /** 乘车人证件号(幂等防重的关键字段) */
        private String idCard;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIdCard() {
            return idCard;
        }

        public void setIdCard(String idCard) {
            this.idCard = idCard;
        }
    }

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public LocalDate getTrainDate() {
        return trainDate;
    }

    public void setTrainDate(LocalDate trainDate) {
        this.trainDate = trainDate;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public void setSeatTypeId(Long seatTypeId) {
        this.seatTypeId = seatTypeId;
    }

    public Long getFromStationId() {
        return fromStationId;
    }

    public void setFromStationId(Long fromStationId) {
        this.fromStationId = fromStationId;
    }

    public Long getToStationId() {
        return toStationId;
    }

    public void setToStationId(Long toStationId) {
        this.toStationId = toStationId;
    }

    public List<Passenger> getPassengers() {
        return passengers;
    }

    public void setPassengers(List<Passenger> passengers) {
        this.passengers = passengers;
    }
}
