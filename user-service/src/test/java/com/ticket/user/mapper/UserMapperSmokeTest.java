package com.ticket.user.mapper;

import com.ticket.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冒烟测试:一次 insert 验证四条链路 ——
 * ① 数据源连得上 MySQL ② MyBatis-Plus 配置生效 ③ 实体-表映射正确 ④ 自定义雪花接入了 ID 生成
 */
@SpringBootTest
class UserMapperSmokeTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertAssignsSnowflakeId() {
        User user = new User();
        // 每次运行用唯一用户名,避免和上次运行的残留数据撞 uk_username
        user.setUsername("smoke-" + System.nanoTime());
        user.setPassword("placeholder");

        userMapper.insert(user);

        // 核心断言:插入前 id 为空,插入后 MP 调用了自定义 IdentifierGenerator 回填雪花 ID
        assertNotNull(user.getId(), "id 应被雪花生成器回填");
        assertTrue(user.getId() > 0, "雪花 ID 应为正数,实际: " + user.getId());

        // 反向验证:能按 id 查回同一条数据 = 实体与表映射正确
        User loaded = userMapper.selectById(user.getId());
        assertEquals(user.getUsername(), loaded.getUsername());

        // 清理,保持测试可重复
        userMapper.deleteById(user.getId());
    }
}
