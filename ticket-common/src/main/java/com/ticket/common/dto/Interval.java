package com.ticket.common.dto;

/**
 * 跨服务 DTO:站序区间(from ≤ to)。
 * 重叠区间列表由 order 域算好随 DeductRequest 传入 —— 避免 ticket-service 再调 train-service(调用链变长)。
 */
public class Interval {

    /** 起始站序 */
    private Integer fromNo;

    /** 到达站序 */
    private Integer toNo;

    public Interval() {
    }

    public Interval(Integer fromNo, Integer toNo) {
        this.fromNo = fromNo;
        this.toNo = toNo;
    }

    public Integer getFromNo() {
        return fromNo;
    }

    public void setFromNo(Integer fromNo) {
        this.fromNo = fromNo;
    }

    public Integer getToNo() {
        return toNo;
    }

    public void setToNo(Integer toNo) {
        this.toNo = toNo;
    }
}
