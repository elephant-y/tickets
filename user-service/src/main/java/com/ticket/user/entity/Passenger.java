package com.ticket.user.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 常用乘车人:账号(user)下储存的乘车人,下单时勾选,不再手输。
 * create_time 由 common AutoFillMetaObjectHandler 以 JVM 时钟填充(不依赖 DB 默认值)。
 */
@TableName ("passenger")
public class Passenger {
    @TableId (type = IdType.ASSIGN_ID)      // 雪花 ID(common 的 IdentifierGenerator)
    private Long id;
    private Long userId;
    private String name;
    private String idCard;
    @TableField (fill = FieldFill.INSERT)
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
