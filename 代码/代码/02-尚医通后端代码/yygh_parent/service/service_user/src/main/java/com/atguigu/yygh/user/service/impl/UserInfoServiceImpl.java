package com.atguigu.yygh.user.service.impl;

import com.atguigu.yygh.common.exception.YyghException;
import com.atguigu.yygh.common.helper.JwtHelper;
import com.atguigu.yygh.common.result.ResultCodeEnum;
import com.atguigu.yygh.enums.AuthStatusEnum;
import com.atguigu.yygh.model.user.Patient;
import com.atguigu.yygh.model.user.UserInfo;
import com.atguigu.yygh.user.mapper.UserInfoMapper;
import com.atguigu.yygh.user.service.PatientService;
import com.atguigu.yygh.user.service.UserInfoService;
import com.atguigu.yygh.vo.user.ChangePasswordVo;
import com.atguigu.yygh.vo.user.LoginVo;
import com.atguigu.yygh.vo.user.PasswordLoginVo;
import com.atguigu.yygh.vo.user.RefreshTokenVo;
import com.atguigu.yygh.vo.user.ResetPasswordVo;
import com.atguigu.yygh.vo.user.UserAuthVo;
import com.atguigu.yygh.vo.user.UserInfoQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class UserInfoServiceImpl  extends
        ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String EMAIL_CODE_KEY_PREFIX = "login:email-code:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh-token:";
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_ITERATIONS = 120000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private PatientService patientService;

    //用户手机号登录接口
    @Override
    public Map<String, Object> loginUser(LoginVo loginVo) {
        //从loginVo获取输入的手机号，和验证码
        String email = normalizeEmail(loginVo.getEmail());
        String phone = StringUtils.isEmpty(email) ? normalizeEmail(loginVo.getPhone()) : email;
        String code = loginVo.getCode();
        String loginKey = buildLoginKey(phone);

        //判断手机号和验证码是否为空
        if(StringUtils.isEmpty(phone) || StringUtils.isEmpty(code)) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        if(phone.contains("@") && !isValidEmail(phone)) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }

        //判断手机验证码和输入的验证码是否一致
        String codeKey = phone.contains("@") ? buildEmailCodeKey(phone) : phone;
        String redisCode = redisTemplate.opsForValue().get(codeKey);
        if(StringUtils.isEmpty(redisCode)) {
            redisCode = redisTemplate.opsForValue().get(phone);
        }
        if(!code.equals(redisCode)) {
            throw new YyghException(ResultCodeEnum.CODE_ERROR);
        }

        //绑定手机号码
        UserInfo userInfo = null;
        if(!StringUtils.isEmpty(loginVo.getOpenid())) {
            userInfo = this.selectWxInfoOpenId(loginVo.getOpenid());
            if(null != userInfo) {
                userInfo.setPhone(loginKey);
                this.updateById(userInfo);
            } else {
                throw new YyghException(ResultCodeEnum.DATA_ERROR);
            }
        }

        //如果userinfo为空，进行正常手机登录
        if(userInfo == null) {
            //判断是否第一次登录：根据手机号查询数据库，如果不存在相同手机号就是第一次登录
            QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
            wrapper.eq("phone",loginKey);
            userInfo = baseMapper.selectOne(wrapper);
            if(userInfo == null) { //第一次使用这个手机号登录
                //添加信息到数据库
                userInfo = new UserInfo();
                userInfo.setName(phone.contains("@") ? buildEmailNickName(phone) : "");
                userInfo.setNickName(phone.contains("@") ? buildEmailNickName(phone) : "");
                userInfo.setPhone(loginKey);
                if(phone.contains("@")) {
                    userInfo.setEmail(phone);
                }
                userInfo.setStatus(1);
                userInfo.setRefreshTokenVersion(0);
                baseMapper.insert(userInfo);
            } else if(phone.contains("@") && StringUtils.isEmpty(userInfo.getEmail())) {
                userInfo.setEmail(phone);
                baseMapper.updateById(userInfo);
            }
        }

        //校验是否被禁用
        if(userInfo.getStatus() == 0) {
            throw new YyghException(ResultCodeEnum.LOGIN_DISABLED_ERROR);
        }

        if(phone.contains("@")) {
            ensureDemoPatient(userInfo);
        }

        //不是第一次，直接登录
        //返回登录信息
        //返回登录用户名
        //返回token信息
        Map<String, Object> map = buildLoginResponse(userInfo, phone.contains("@") ? phone : null);
        redisTemplate.delete(codeKey);
        return map;
    }

    @Override
    public Map<String, Object> passwordLogin(PasswordLoginVo loginVo) {
        if(loginVo == null) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        String email = normalizeEmail(loginVo.getEmail());
        String password = loginVo.getPassword();
        if(!isValidEmail(email) || StringUtils.isEmpty(password)) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }

        UserInfo userInfo = findByEmail(email);
        if(userInfo == null || StringUtils.isEmpty(userInfo.getPasswordHash())
                || !verifyPassword(password, userInfo.getPasswordHash())) {
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        if(userInfo.getStatus() == 0) {
            throw new YyghException(ResultCodeEnum.LOGIN_DISABLED_ERROR);
        }

        String name = StringUtils.isEmpty(userInfo.getName()) ? email : userInfo.getName();
        return buildLoginResponse(userInfo, name);
    }

    @Override
    public Map<String, Object> refreshToken(RefreshTokenVo refreshTokenVo) {
        if(refreshTokenVo == null || StringUtils.isEmpty(refreshTokenVo.getRefreshToken())) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        String refreshToken = refreshTokenVo.getRefreshToken();
        if(!"refresh".equals(JwtHelper.getTokenType(refreshToken))) {
            throw new YyghException(ResultCodeEnum.LOGIN_AUTH);
        }
        String redisKey = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String redisUserId = redisTemplate.opsForValue().get(redisKey);
        Long userId = JwtHelper.getUserId(refreshToken);
        if(StringUtils.isEmpty(redisUserId) || userId == null || !redisUserId.equals(String.valueOf(userId))) {
            throw new YyghException(ResultCodeEnum.LOGIN_AUTH);
        }

        UserInfo userInfo = baseMapper.selectById(userId);
        if(userInfo == null || userInfo.getStatus() == 0) {
            throw new YyghException(ResultCodeEnum.LOGIN_AUTH);
        }
        Integer tokenVersion = JwtHelper.getTokenVersion(refreshToken);
        if(tokenVersion != null && userInfo.getRefreshTokenVersion() != null
                && !tokenVersion.equals(userInfo.getRefreshTokenVersion())) {
            throw new YyghException(ResultCodeEnum.LOGIN_AUTH);
        }

        String name = StringUtils.isEmpty(userInfo.getEmail()) ? userInfo.getPhone() : userInfo.getEmail();
        return buildLoginResponse(userInfo, name);
    }

    @Override
    public void resetPassword(ResetPasswordVo resetPasswordVo) {
        if(resetPasswordVo == null) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        String email = normalizeEmail(resetPasswordVo.getEmail());
        String code = resetPasswordVo.getCode();
        String newPassword = resetPasswordVo.getNewPassword();
        if(!isValidEmail(email) || StringUtils.isEmpty(code) || !isValidPassword(newPassword)) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        String codeKey = buildEmailCodeKey(email);
        String redisCode = redisTemplate.opsForValue().get(codeKey);
        if(StringUtils.isEmpty(redisCode) || !code.equals(redisCode)) {
            throw new YyghException(ResultCodeEnum.CODE_ERROR);
        }

        UserInfo userInfo = findByEmail(email);
        if(userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setEmail(email);
            userInfo.setPhone(buildLoginKey(email));
            userInfo.setName(buildEmailNickName(email));
            userInfo.setNickName(buildEmailNickName(email));
            userInfo.setStatus(1);
            userInfo.setRefreshTokenVersion(0);
            baseMapper.insert(userInfo);
        }
        userInfo.setPasswordHash(hashPassword(newPassword));
        userInfo.setRefreshTokenVersion(nextRefreshTokenVersion(userInfo));
        baseMapper.updateById(userInfo);
        redisTemplate.delete(codeKey);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordVo changePasswordVo) {
        if(userId == null || changePasswordVo == null || !isValidPassword(changePasswordVo.getNewPassword())) {
            throw new YyghException(ResultCodeEnum.PARAM_ERROR);
        }
        UserInfo userInfo = baseMapper.selectById(userId);
        if(userInfo == null) {
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        if(!StringUtils.isEmpty(userInfo.getPasswordHash())
                && !verifyPassword(changePasswordVo.getOldPassword(), userInfo.getPasswordHash())) {
            throw new YyghException(ResultCodeEnum.DATA_ERROR);
        }
        userInfo.setPasswordHash(hashPassword(changePasswordVo.getNewPassword()));
        userInfo.setRefreshTokenVersion(nextRefreshTokenVersion(userInfo));
        baseMapper.updateById(userInfo);
    }

    private Map<String, Object> buildLoginResponse(UserInfo userInfo, String displayName) {
        Map<String, Object> map = new HashMap<>();
        String name = userInfo.getName();
        if(StringUtils.isEmpty(name)) {
            name = userInfo.getNickName();
        }
        if(StringUtils.isEmpty(name)) {
            name = userInfo.getPhone();
        }
        if(!StringUtils.isEmpty(displayName)) {
            name = displayName;
        }
        map.put("name",name);

        String token = JwtHelper.createToken(userInfo.getId(), name);
        map.put("token",token);
        Integer tokenVersion = userInfo.getRefreshTokenVersion() == null ? 0 : userInfo.getRefreshTokenVersion();
        String refreshToken = JwtHelper.createRefreshToken(userInfo.getId(), tokenVersion);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_KEY_PREFIX + refreshToken,
                String.valueOf(userInfo.getId()), 14, TimeUnit.DAYS);
        map.put("refreshToken", refreshToken);
        map.put("userId", userInfo.getId());
        if(!StringUtils.isEmpty(userInfo.getEmail())) {
            map.put("email", userInfo.getEmail());
        }
        return map;
    }

    private UserInfo findByEmail(String email) {
        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        UserInfo userInfo = baseMapper.selectOne(wrapper);
        if(userInfo != null) {
            return userInfo;
        }
        QueryWrapper<UserInfo> legacyWrapper = new QueryWrapper<>();
        legacyWrapper.eq("phone", buildLoginKey(email));
        return baseMapper.selectOne(legacyWrapper);
    }

    private int nextRefreshTokenVersion(UserInfo userInfo) {
        return userInfo.getRefreshTokenVersion() == null ? 1 : userInfo.getRefreshTokenVersion() + 1;
    }

    private boolean isValidPassword(String password) {
        return !StringUtils.isEmpty(password) && password.length() >= 8 && password.length() <= 72;
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[PASSWORD_SALT_BYTES];
            SECURE_RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS);
            return "pbkdf2$" + PASSWORD_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new YyghException(ResultCodeEnum.SERVICE_ERROR);
        }
    }

    private boolean verifyPassword(String password, String encoded) {
        if(StringUtils.isEmpty(password) || StringUtils.isEmpty(encoded)) {
            return false;
        }
        try {
            String[] parts = encoded.split("\\$");
            if(parts.length != 4 || !"pbkdf2".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
            return constantTimeEquals(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations, int keyBits) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

    private boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if(expected == null || actual == null || expected.length != actual.length) {
            return false;
        }
        int result = 0;
        for(int i = 0; i < expected.length; i++) {
            result |= expected[i] ^ actual[i];
        }
        return result == 0;
    }

    private void ensureDemoPatient(UserInfo userInfo) {
        if(userInfo == null || userInfo.getId() == null) {
            return;
        }
        List<Patient> patientList = patientService.findAllUserId(userInfo.getId());
        if(patientList != null && !patientList.isEmpty()) {
            return;
        }

        Patient patient = new Patient();
        patient.setUserId(userInfo.getId());
        patient.setName(StringUtils.isEmpty(userInfo.getName()) ? "本地演示就诊人" : userInfo.getName());
        patient.setCertificatesType("10");
        patient.setCertificatesNo(buildDemoCertificatesNo(userInfo.getId()));
        patient.setSex(1);
        patient.setBirthdate(new Date(631123200000L));
        patient.setPhone(buildDemoPhone(userInfo.getId()));
        patient.setIsMarry(0);
        patient.setProvinceCode("110000");
        patient.setCityCode("110100");
        patient.setDistrictCode("110102");
        patient.setAddress("北京市西城区本地演示地址");
        patient.setContactsName("本地演示联系人");
        patient.setContactsCertificatesType("10");
        patient.setContactsCertificatesNo(buildDemoCertificatesNo(userInfo.getId() + 1000));
        patient.setContactsPhone("13900000001");
        patient.setCardNo("CARD-DEMO-" + userInfo.getId());
        patient.setIsInsure(1);
        patient.setStatus("1");
        patientService.save(patient);
    }

    private String buildDemoPhone(Long userId) {
        long suffix = userId == null ? 0L : Math.abs(userId % 100000000L);
        return "13" + String.format("%09d", suffix);
    }

    private String buildDemoCertificatesNo(Long userId) {
        long suffix = userId == null ? 1L : Math.abs(userId % 10000L);
        return "110101199001" + String.format("%02d", suffix % 28 + 1) + String.format("%04d", suffix);
    }

    private String buildLoginKey(String account) {
        if(!StringUtils.isEmpty(account) && account.contains("@")) {
            long hash = Integer.toUnsignedLong(account.toLowerCase().hashCode()) % 10000000000L;
            return "E" + String.format("%010d", hash);
        }
        return account;
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

    private String buildEmailNickName(String email) {
        String localPart = email.substring(0, email.indexOf("@"));
        if(localPart.length() > 16) {
            return localPart.substring(0, 16);
        }
        return localPart;
    }

    @Override
    public UserInfo selectWxInfoOpenId(String openid) {
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("openid",openid);
        UserInfo userInfo = baseMapper.selectOne(queryWrapper);
        return userInfo;
    }

    //用户认证
    @Override
    public void userAuth(Long userId, UserAuthVo userAuthVo) {
        //根据用户id查询用户信息
        UserInfo userInfo = baseMapper.selectById(userId);
        //设置认证信息
        //认证人姓名
        userInfo.setName(userAuthVo.getName());
        //其他认证信息
        userInfo.setCertificatesType(userAuthVo.getCertificatesType());
        userInfo.setCertificatesNo(userAuthVo.getCertificatesNo());
        userInfo.setCertificatesUrl(userAuthVo.getCertificatesUrl());
        userInfo.setAuthStatus(AuthStatusEnum.AUTH_RUN.getStatus());
        //进行信息更新
        baseMapper.updateById(userInfo);
    }

    //用户列表（条件查询带分页）
    @Override
    public IPage<UserInfo> selectPage(Page<UserInfo> pageParam, UserInfoQueryVo userInfoQueryVo) {
        //UserInfoQueryVo获取条件值
        String name = userInfoQueryVo.getKeyword(); //用户名称
        Integer status = userInfoQueryVo.getStatus();//用户状态
        Integer authStatus = userInfoQueryVo.getAuthStatus(); //认证状态
        String createTimeBegin = userInfoQueryVo.getCreateTimeBegin(); //开始时间
        String createTimeEnd = userInfoQueryVo.getCreateTimeEnd(); //结束时间
        //对条件值进行非空判断
        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        if(!StringUtils.isEmpty(name)) {
            wrapper.like("name",name);
        }
        if(!StringUtils.isEmpty(status)) {
            wrapper.eq("status",status);
        }
        if(!StringUtils.isEmpty(authStatus)) {
            wrapper.eq("auth_status",authStatus);
        }
        if(!StringUtils.isEmpty(createTimeBegin)) {
            wrapper.ge("create_time",createTimeBegin);
        }
        if(!StringUtils.isEmpty(createTimeEnd)) {
            wrapper.le("create_time",createTimeEnd);
        }
        //调用mapper的方法
        IPage<UserInfo> pages = baseMapper.selectPage(pageParam, wrapper);
        //编号变成对应值封装
        pages.getRecords().stream().forEach(item -> {
            this.packageUserInfo(item);
        });
        return pages;
    }

    //用户锁定
    @Override
    public void lock(Long userId, Integer status) {
        if(status.intValue()==0 || status.intValue()==1) {
            UserInfo userInfo = baseMapper.selectById(userId);
            userInfo.setStatus(status);
            baseMapper.updateById(userInfo);
        }
    }

    //用户详情
    @Override
    public Map<String, Object> show(Long userId) {
        Map<String,Object> map = new HashMap<>();
        //根据userid查询用户信息
        UserInfo userInfo = this.packageUserInfo(baseMapper.selectById(userId));
        map.put("userInfo",userInfo);
        //根据userid查询就诊人信息
        List<Patient> patientList = patientService.findAllUserId(userId);
        map.put("patientList",patientList);
        return map;
    }

    //认证审批  2通过  -1不通过
    @Override
    public void approval(Long userId, Integer authStatus) {
        if(authStatus.intValue()==2 || authStatus.intValue()==-1) {
            UserInfo userInfo = baseMapper.selectById(userId);
            userInfo.setAuthStatus(authStatus);
            baseMapper.updateById(userInfo);
        }
    }

    //编号变成对应值封装
    private UserInfo packageUserInfo(UserInfo userInfo) {
        //处理认证状态编码
        userInfo.getParam().put("authStatusString",AuthStatusEnum.getStatusNameByStatus(userInfo.getAuthStatus()));
        //处理用户状态 0  1
        String statusString = userInfo.getStatus().intValue()==0 ?"锁定" : "正常";
        userInfo.getParam().put("statusString",statusString);
        return userInfo;
    }
}
