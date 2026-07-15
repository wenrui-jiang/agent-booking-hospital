package com.atguigu.yygh.vo.user;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "刷新令牌")
public class RefreshTokenVo {

    @ApiModelProperty(value = "刷新令牌")
    private String refreshToken;
}
