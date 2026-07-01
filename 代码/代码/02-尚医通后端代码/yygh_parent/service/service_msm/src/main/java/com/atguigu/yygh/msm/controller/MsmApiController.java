package com.atguigu.yygh.msm.controller;

import com.atguigu.yygh.common.result.Result;
import com.atguigu.yygh.msm.service.MsmService;
import com.atguigu.yygh.msm.utils.RandomUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/msm")
@CrossOrigin
public class MsmApiController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String EMAIL_CODE_KEY_PREFIX = "login:email-code:";

    @Autowired
    private MsmService msmService;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Value("${yygh.msm.dev-mode:false}")
    private boolean devMode;

    @Value("${yygh.msm.dev-code:123456}")
    private String devCode;

    @PostMapping("email/code")
    public Result sendEmailCode(@RequestBody Map<String, String> request) {
        String email = normalizeEmail(request == null ? null : request.get("email"));
        if (!isValidEmail(email)) {
            return Result.fail().message("邮箱地址不正确");
        }

        return sendAndCacheCode(email, buildEmailCodeKey(email));
    }

    @GetMapping("send/{account}")
    public Result sendCode(@PathVariable String account) {
        account = normalizeEmail(account);
        if (StringUtils.isEmpty(account)) {
            return Result.fail().message("账号不能为空");
        }

        String redisKey = account.contains("@") ? buildEmailCodeKey(account) : account;
        return sendAndCacheCode(account, redisKey);
    }

    private Result sendAndCacheCode(String account, String redisKey) {
        if (devMode) {
            redisTemplate.opsForValue().set(redisKey, devCode, 24, TimeUnit.HOURS);
            return Result.ok();
        }

        String code = RandomUtil.getSixBitRandom();
        boolean isSend = msmService.send(account, code);
        if (isSend) {
            redisTemplate.opsForValue().set(redisKey, code, 5, TimeUnit.MINUTES);
            return Result.ok();
        }

        return Result.fail().message("验证码发送失败");
    }

    private String normalizeEmail(String email) {
        return StringUtils.isEmpty(email) ? "" : email.trim().toLowerCase();
    }

    private boolean isValidEmail(String email) {
        return !StringUtils.isEmpty(email) && EMAIL_PATTERN.matcher(email).matches();
    }

    private String buildEmailCodeKey(String email) {
        return EMAIL_CODE_KEY_PREFIX + email;
    }
}
