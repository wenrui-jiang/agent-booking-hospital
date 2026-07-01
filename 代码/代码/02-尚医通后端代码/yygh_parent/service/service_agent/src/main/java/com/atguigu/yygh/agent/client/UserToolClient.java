package com.atguigu.yygh.agent.client;

import com.atguigu.yygh.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "service-user")
public interface UserToolClient {

    @GetMapping("/api/user/patient/auth/findAll")
    Result<Object> listPatients(@RequestHeader("token") String token);
}
