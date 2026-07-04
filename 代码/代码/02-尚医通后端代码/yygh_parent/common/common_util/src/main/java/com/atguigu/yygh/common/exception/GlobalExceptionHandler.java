package com.atguigu.yygh.common.exception;

import com.atguigu.yygh.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e) {
        log.error("Unhandled request exception", e);
        return Result.build(201, e.getMessage() == null ? "失败" : e.getMessage());
    }

    @ExceptionHandler(YyghException.class)
    @ResponseBody
    public Result error(YyghException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return Result.build(e.getCode(), e.getMessage());
    }
}
