package com.atguigu.yygh.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ServiceAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceAgentApplication.class, args);
    }
}
