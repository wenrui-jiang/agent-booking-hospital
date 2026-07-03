package com.atguigu.yygh.vo.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "重置密码")
public class ResetPasswordVo {

    @ApiModelProperty(value = "邮箱")
    private String email;

    @ApiModelProperty(value = "邮箱验证码")
    private String code;

    @ApiModelProperty(value = "新密码")
    private String newPassword;
}
