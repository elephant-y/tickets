package com.ticket.user.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.user.dto.PassengerRequest;
import com.ticket.user.entity.Passenger;
import com.ticket.user.mapper.PassengerMapper;

@Service
public class PassengerService {
    private final PassengerMapper passengerMapper;
    public PassengerService(PassengerMapper passengerMapper)
    {
        this.passengerMapper=passengerMapper;
    }
    /** 当前账号的乘车人列表;按 id 升序 = 添加先后(雪花 ID 单调递增) */
    public List<Passenger> list(Long uid)
    {
        return passengerMapper.selectList(
                new LambdaQueryWrapper<Passenger>()
                        .eq(Passenger::getUserId, uid)
                        .orderByAsc(Passenger::getId));
    }
    public void add(Long uid, PassengerRequest request)
    {
        // 证件号是 seat_hold 防重的关键字段(uk_hold),格式乱会误伤他人,入库前把关
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BizException(400, "姓名不能为空");
        }
        if (request.getIdCard() == null || !request.getIdCard().matches("\\d{17}[0-9Xx]")) {
            throw new BizException(400, "证件号格式不正确(18位)");
        }
        Passenger passenger=new Passenger();
        passenger.setUserId(uid);
        passenger.setName(request.getName());
        passenger.setIdCard(request.getIdCard().toUpperCase());
        try {
            passengerMapper.insert(passenger);   // 撞 uk_user_card = 本账号已存过该证件
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该证件号已在你的乘车人列表");
        }
    }
    /** 删除:user_id 直接写进 WHERE —— 归属校验与删除一步完成(原子),
     *  不存在"先查后删"的竞态窗口,天然防越权删别人的乘车人 */
    public void delete(Long uid, Long id)
    {
        int affected = passengerMapper.delete(
                new LambdaQueryWrapper<Passenger>()
                        .eq(Passenger::getId, id)
                        .eq(Passenger::getUserId, uid));
        if (affected == 0) {
            throw new BizException(404, "乘车人不存在");
        }
    }
}
