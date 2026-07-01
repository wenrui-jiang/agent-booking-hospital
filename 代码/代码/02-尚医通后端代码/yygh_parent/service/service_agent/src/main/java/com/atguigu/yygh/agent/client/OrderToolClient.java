package com.atguigu.yygh.agent.client;

import com.atguigu.yygh.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "service-order")
public interface OrderToolClient {

    @PostMapping("/api/order/orderInfo/auth/submitOrder/{scheduleId}/{patientId}")
    Result<Object> submitOrder(@PathVariable("scheduleId") String scheduleId,
                               @PathVariable("patientId") Long patientId,
                               @RequestHeader("token") String token);

    @GetMapping("/api/order/orderInfo/auth/getOrders/{orderId}")
    Result<Object> getOrderInfo(@PathVariable("orderId") Long orderId,
                                @RequestHeader("token") String token);

    @GetMapping("/api/order/weixin/createNative/{orderId}")
    Result<Object> createPaymentQrCode(@PathVariable("orderId") Long orderId);

    @GetMapping("/api/order/weixin/queryPayStatus/{orderId}")
    Result<Object> queryPaymentStatus(@PathVariable("orderId") Long orderId);
}
