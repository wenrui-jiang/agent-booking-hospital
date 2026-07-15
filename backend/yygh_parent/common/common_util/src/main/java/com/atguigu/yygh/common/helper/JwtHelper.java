package com.atguigu.yygh.common.helper;

import io.jsonwebtoken.*;
import org.springframework.util.StringUtils;

import java.util.Date;

public class JwtHelper {

    //过期时间
    private static long tokenExpiration = 24*60*60*1000;
    private static long refreshTokenExpiration = 14L*24*60*60*1000;
    //签名秘钥
    private static String tokenSignKey = "123456";

    //根据参数生成token
    public static String createToken(Long userId, String userName) {
        String token = Jwts.builder()
                .setSubject("YYGH-USER")
                .setExpiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .claim("userId", userId)
                .claim("userName", userName)
                .claim("tokenType", "access")
                .signWith(SignatureAlgorithm.HS512, tokenSignKey)
                .compressWith(CompressionCodecs.GZIP)
                .compact();
        return token;
    }

    public static String createRefreshToken(Long userId, Integer tokenVersion) {
        return Jwts.builder()
                .setSubject("YYGH-REFRESH")
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .claim("userId", userId)
                .claim("tokenType", "refresh")
                .claim("tokenVersion", tokenVersion == null ? 0 : tokenVersion)
                .signWith(SignatureAlgorithm.HS512, tokenSignKey)
                .compressWith(CompressionCodecs.GZIP)
                .compact();
    }

    //根据token字符串得到用户id
    public static Long getUserId(String token) {
        if(StringUtils.isEmpty(token)) return null;

        Jws<Claims> claimsJws = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        Object userId = claims.get("userId");
        if(userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    //根据token字符串得到用户名称
    public static String getUserName(String token) {
        if(StringUtils.isEmpty(token)) return "";

        Jws<Claims> claimsJws = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        return (String)claims.get("userName");
    }

    public static String getTokenType(String token) {
        if(StringUtils.isEmpty(token)) return "";

        Jws<Claims> claimsJws = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        Object tokenType = claims.get("tokenType");
        return tokenType == null ? "" : tokenType.toString();
    }

    public static Integer getTokenVersion(String token) {
        if(StringUtils.isEmpty(token)) return null;

        Jws<Claims> claimsJws = Jwts.parser().setSigningKey(tokenSignKey).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        Object tokenVersion = claims.get("tokenVersion");
        if(tokenVersion instanceof Integer) {
            return (Integer) tokenVersion;
        }
        return tokenVersion == null ? null : Integer.valueOf(tokenVersion.toString());
    }

    public static void main(String[] args) {
        String token = JwtHelper.createToken(1L, "lucy");
        System.out.println(token);
        System.out.println(JwtHelper.getUserId(token));
        System.out.println(JwtHelper.getUserName(token));
    }
}

