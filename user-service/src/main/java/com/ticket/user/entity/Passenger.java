package com.ticket.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 常用乘车人:账号(user)下储存的乘车人,下单时勾选,不再手输。
 * create_time 由 DB 默认值 CURRENT_TIMESTAMP 填充(库时区已统一 +08:00)。
 */
@TableName ("passenger")
public class Passenger {
    @TableId (type = IdType.ASSIGN_ID)      // 雪花 ID(common 的 IdentifierGenerator)
    private Long id;
    private Long userId;
    private String name;
    private String idCard;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
