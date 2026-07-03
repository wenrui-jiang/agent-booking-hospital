package com.atguigu.yygh.user.api;

import com.atguigu.yygh.common.result.Result;
import com.atguigu.yygh.common.utils.AuthContextHolder;
import com.atguigu.yygh.model.user.UserInfo;
import com.atguigu.yygh.user.service.UserInfoService;
import com.atguigu.yygh.vo.user.ChangePasswordVo;
import com.atguigu.yygh.vo.user.LoginVo;
import com.atguigu.yygh.vo.user.PasswordLoginVo;
import com.atguigu.yygh.vo.user.RefreshTokenVo;
import com.atguigu.yygh.vo.user.ResetPasswordVo;
import com.atguigu.yygh.vo.user.UserAuthVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class UserInfoApiController {

    @Autowired
    private UserInfoService userInfoService;

    //用户手机号登录接口
    @PostMapping("login")
    public Result login(@RequestBody LoginVo loginVo) {
        Map<String,Object> info = userInfoService.loginUser(loginVo);
        return Result.ok(info);
    }

    @PostMapping("password/login")
    public Result passwordLogin(@RequestBody PasswordLoginVo loginVo) {
        Map<String,Object> info = userInfoService.passwordLogin(loginVo);
        return Result.ok(info);
    }

    @PostMapping("password/reset")
    public Result resetPassword(@RequestBody ResetPasswordVo resetPasswordVo) {
        userInfoService.resetPassword(resetPasswordVo);
        return Result.ok();
    }

    @PostMapping("token/refresh")
    public Result refreshToken(@RequestBody RefreshTokenVo refreshTokenVo) {
        Map<String,Object> info = userInfoService.refreshToken(refreshTokenVo);
        return Result.ok(info);
    }

    @PostMapping("auth/password/change")
    public Result changePassword(@RequestBody ChangePasswordVo changePasswordVo, HttpServletRequest request) {
        userInfoService.changePassword(AuthContextHolder.getUserId(request), changePasswordVo);
        return Result.ok();
    }

    //用户认证接口
    @PostMapping("auth/userAuth")
    public Result userAuth(@RequestBody UserAuthVo userAuthVo, HttpServletRequest request) {
        //传递两个参数，第一个参数用户id，第二个参数认证数据vo对象
        userInfoService.userAuth(AuthContextHolder.getUserId(request),userAuthVo);
        return Result.ok();
    }

    //获取用户id信息接口
    @GetMapping("auth/getUserInfo")
    public Result getUserInfo(HttpServletRequest request) {
        Long userId = AuthContextHolder.getUserId(request);
        UserInfo userInfo = userInfoService.getById(userId);
        return Result.ok(userInfo);
    }
}
