package com.ticket.user.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.common.util.JwtUtil;
import com.ticket.user.dto.LoginRequest;
import com.ticket.user.dto.LoginResponse;
import com.ticket.user.dto.RegisterRequest;
import com.ticket.user.dto.UserInfoResponse;
import com.ticket.user.entity.User;
import com.ticket.user.mapper.UserMapper;

@Service 
public class UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    public UserService(UserMapper userMapper,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {    // 构造器注入:依赖一目了然,字段可 final
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    public void register(RegisterRequest request)
    {
        User user=new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
         try {
            userMapper.insert(user);         // 雪花 ID 在这被回填
        } catch (DuplicateKeyException e) {  // 并发注册的原子防线(决策 1)
            throw new BizException(409, "用户名已存在");
        }
    }
    public LoginResponse login(LoginRequest request)
    {
        User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");  
        }
        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtUtil.getExpireHours());   
        return response;
    }
    public UserInfoResponse me(Long uid) {
        User user=userMapper.selectById(uid); 
        if(user==null) throw new BizException(404, "用户不存在");
        UserInfoResponse userInfoResponse=new UserInfoResponse();
        userInfoResponse.setId(uid);
        userInfoResponse.setUsername(user.getUsername());
        return userInfoResponse;  
    }

}
