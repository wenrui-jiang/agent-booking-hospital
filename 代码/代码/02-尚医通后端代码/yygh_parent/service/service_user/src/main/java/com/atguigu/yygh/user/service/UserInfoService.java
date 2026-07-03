package com.atguigu.yygh.user.service;

import com.atguigu.yygh.model.hosp.HospitalSet;
import com.atguigu.yygh.model.user.UserInfo;
import com.atguigu.yygh.vo.user.ChangePasswordVo;
import com.atguigu.yygh.vo.user.LoginVo;
import com.atguigu.yygh.vo.user.PasswordLoginVo;
import com.atguigu.yygh.vo.user.RefreshTokenVo;
import com.atguigu.yygh.vo.user.ResetPasswordVo;
import com.atguigu.yygh.vo.user.UserAuthVo;
import com.atguigu.yygh.vo.user.UserInfoQueryVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface UserInfoService extends IService<UserInfo> {
    //用户手机号登录接口
    Map<String, Object> loginUser(LoginVo loginVo);

    Map<String, Object> passwordLogin(PasswordLoginVo loginVo);

    Map<String, Object> refreshToken(RefreshTokenVo refreshTokenVo);

    void resetPassword(ResetPasswordVo resetPasswordVo);

    void changePassword(Long userId, ChangePasswordVo changePasswordVo);

    //根据openid判断
    UserInfo selectWxInfoOpenId(String openid);

    //用户认证
    void userAuth(Long userId, UserAuthVo userAuthVo);

    //用户列表（条件查询带分页）
    IPage<UserInfo> selectPage(Page<UserInfo> pageParam, UserInfoQueryVo userInfoQueryVo);

    //用户锁定
    void lock(Long userId, Integer status);

    //用户详情
    Map<String, Object> show(Long userId);

    //认证审批
    void approval(Long userId, Integer authStatus);
}
