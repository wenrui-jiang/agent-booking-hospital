package com.atguigu.yygh.agent.controller;

import com.atguigu.yygh.common.exception.YyghException;
import com.atguigu.yygh.common.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(YyghException.class)
    @ResponseBody
    public Result<?> handleYyghException(YyghException e) {
        return Result.build(e.getCode(), e.getMessage());
    }
}
