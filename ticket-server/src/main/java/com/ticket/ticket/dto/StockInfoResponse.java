package com.ticket.ticket.dto;

public class StockInfoResponse {
    private Long seatTypeId;
    private String seatTypeName;
    private Integer fromStationNo;
    private Integer toStationNo;
    private Integer totalSeat;
    private Integer soldSeat;
    private Integer available;
    public Long getSeatTypeId() {
        return seatTypeId;
    }
    public void setSeatTypeId(Long seatTypeId) {
        this.seatTypeId = seatTypeId;
    }
    public String getSeatTypeName() {
        return seatTypeName;
    }
    public void setSeatTypeName(String seatTypeName) {
        this.seatTypeName = seatTypeName;
    }
    public Integer getFromStationNo() {
        return fromStationNo;
    }
    public void setFromStationNo(Integer fromStationNo) {
        this.fromStationNo = fromStationNo;
    }
    public Integer getToStationNo() {
        return toStationNo;
    }
    public void setToStationNo(Integer toStationNo) {
        this.toStationNo = toStationNo;
    }
    public Integer getTotalSeat() {
        return totalSeat;
    }
    public void setTotalSeat(Integer totalSeat) {
        this.totalSeat = totalSeat;
    }
    public Integer getSoldSeat() {
        return soldSeat;
    }
    public void setSoldSeat(Integer soldSeat) {
        this.soldSeat = soldSeat;
    }
    public Integer getAvailable() {
        return available;
    }
    public void setAvailable(Integer available) {
        this.available = available;
    }
}
