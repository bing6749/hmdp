package com.hmdp.utils;
/*
 * @author  MaRui
 * @date  2024/7/29 16:15
 * @version 1.0
 */


import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author MaRui
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断用户是否登录
        if (UserHolder.getUser() == null) {
            //4.不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        //存在 放行
        return true;
    }


}
