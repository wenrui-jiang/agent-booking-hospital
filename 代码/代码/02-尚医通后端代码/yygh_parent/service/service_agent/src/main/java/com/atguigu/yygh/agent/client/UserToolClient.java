package com.atguigu.yygh.agent.client;

import com.atguigu.yygh.common.result.Result;
import com.atguigu.yygh.model.user.Patient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "service-user")
public interface UserToolClient {

    @GetMapping("/api/user/patient/auth/findAll")
    Result<Object> listPatients(@RequestHeader("token") String token);

    @PostMapping("/api/user/patient/auth/save")
    Result<Object> savePatient(@RequestBody Patient patient, @RequestHeader("token") String token);
}
