package com.atguigu.yygh.msm.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.atguigu.yygh.msm.service.MsmService;
import com.atguigu.yygh.msm.utils.ConstantPropertiesUtils;
import com.atguigu.yygh.vo.msm.MsmVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class MsmServiceImpl implements MsmService {

    @Value("${yygh.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${yygh.mail.host:smtp.bjtu.edu.cn}")
    private String mailHost;

    @Value("${yygh.mail.port:465}")
    private int mailPort;

    @Value("${yygh.mail.username:}")
    private String mailUsername;

    @Value("${yygh.mail.auth-username:}")
    private String mailAuthUsername;

    @Value("${yygh.mail.from-address:}")
    private String mailFromAddress;

    @Value("${yygh.mail.password:}")
    private String mailPassword;

    @Value("${yygh.mail.ssl:true}")
    private boolean mailSsl;

    @Value("${yygh.mail.starttls:false}")
    private boolean mailStarttls;

    @Value("${yygh.mail.from-name:YYGH}")
    private String mailFromName;

    @Value("${yygh.mail.connection-timeout-ms:5000}")
    private int mailConnectionTimeoutMs;

    @Value("${yygh.mail.timeout-ms:5000}")
    private int mailTimeoutMs;

    @Value("${yygh.mail.write-timeout-ms:5000}")
    private int mailWriteTimeoutMs;

    @Override
    public boolean send(String phone, String code) {
        if (StringUtils.isEmpty(phone)) {
            return false;
        }

        if (phone.contains("@")) {
            return sendEmailCode(phone, code);
        }

        return sendSmsCode(phone, code);
    }

    @Override
    public boolean send(MsmVo msmVo) {
        if(!StringUtils.isEmpty(msmVo.getPhone())) {
            return this.send(msmVo.getPhone(), msmVo.getParam());
        }
        return false;
    }

    private boolean sendEmailCode(String email, String code) {
        String password = getMailPassword();
        if (!mailEnabled || StringUtils.isEmpty(mailHost) || StringUtils.isEmpty(mailUsername) || StringUtils.isEmpty(password)) {
            return false;
        }

        String subject = "YYGH login verification code";
        String body = "Your YYGH login verification code is: " + code
                + ". It is valid for 5 minutes. If this was not you, ignore this email.";
        try {
            sendJavaMail(email, subject, body);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email verification code to {}", email, e);
            return false;
        }
    }

    private void sendJavaMail(String to, String subject, String body) {
        String username = StringUtils.isEmpty(mailAuthUsername) ? mailUsername.trim() : mailAuthUsername.trim();
        String fromAddress = StringUtils.isEmpty(mailFromAddress) ? mailUsername.trim() : mailFromAddress.trim();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost);
        sender.setPort(mailPort);
        sender.setUsername(username);
        sender.setPassword(getMailPassword().replaceAll("\\s+", ""));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.debug", "false");
        properties.put("mail.smtp.connectiontimeout", String.valueOf(mailConnectionTimeoutMs));
        properties.put("mail.smtp.timeout", String.valueOf(mailTimeoutMs));
        properties.put("mail.smtp.writetimeout", String.valueOf(mailWriteTimeoutMs));
        if (mailSsl) {
            properties.put("mail.smtp.socketFactoryClass", "javax.net.ssl.SSLSocketFactory");
        }
        if (mailStarttls) {
            properties.put("mail.smtp.starttls.enable", "true");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        log.info("Sending login verification email from {} to {}", fromAddress, to);
        sender.send(message);
    }

    private void sendSmtpMail(String to, String subject, String body) throws Exception {
        Socket socket = mailSsl
                ? SSLSocketFactory.getDefault().createSocket(mailHost, mailPort)
                : new Socket(mailHost, mailPort);

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            expect(reader, 220);
            sendCommand(writer, reader, "EHLO localhost", 250);
            if (mailStarttls && !mailSsl) {
                sendCommand(writer, reader, "STARTTLS", 220);
                socket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, mailHost, mailPort, true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                sendCommand(writer, reader, "EHLO localhost", 250);
            }
            sendCommand(writer, reader, "AUTH LOGIN", 334);
            String username = StringUtils.isEmpty(mailAuthUsername) ? mailUsername.trim() : mailAuthUsername.trim();
            String fromAddress = StringUtils.isEmpty(mailFromAddress) ? mailUsername.trim() : mailFromAddress.trim();
            String password = getMailPassword().replaceAll("\\s+", "");
            sendCommand(writer, reader, base64(username), 334);
            sendCommand(writer, reader, base64(password), 235);
            sendCommand(writer, reader, "MAIL FROM:<" + fromAddress + ">", 250);
            sendCommand(writer, reader, "RCPT TO:<" + to + ">", 250);
            sendCommand(writer, reader, "DATA", 354);

            writer.write(buildMessage(to, subject, body));
            writer.write("\r\n.\r\n");
            writer.flush();
            expect(reader, 250);
            sendCommand(writer, reader, "QUIT", 221);
        } finally {
            socket.close();
        }
    }

    private String buildMessage(String to, String subject, String body) {
        String encodedSubject = "=?UTF-8?B?" + base64(subject) + "?=";
        String encodedFromName = "=?UTF-8?B?" + base64(mailFromName) + "?=";
        String fromAddress = StringUtils.isEmpty(mailFromAddress) ? mailUsername.trim() : mailFromAddress.trim();
        return "From: " + encodedFromName + " <" + fromAddress + ">\r\n"
                + "To: <" + to + ">\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(body.getBytes(StandardCharsets.UTF_8))
                + "\r\n";
    }

    private void sendCommand(BufferedWriter writer, BufferedReader reader, String command, int expectedCode) throws Exception {
        writer.write(command + "\r\n");
        writer.flush();
        expect(reader, expectedCode);
    }

    private void expect(BufferedReader reader, int expectedCode) throws Exception {
        String line = reader.readLine();
        if (line == null) {
            throw new IllegalStateException("SMTP server closed connection");
        }
        StringBuilder response = new StringBuilder(line);
        int code = parseSmtpCode(line);
        while (line.length() >= 4 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("SMTP server closed connection");
            }
            response.append(" | ").append(line);
            code = parseSmtpCode(line);
        }
        if (code != expectedCode) {
            throw new IllegalStateException("Unexpected SMTP response " + code
                    + ", expected " + expectedCode + ": " + response);
        }
    }

    private int parseSmtpCode(String line) {
        if (line.length() < 3) {
            return -1;
        }
        return Integer.parseInt(line.substring(0, 3));
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String getMailPassword() {
        if (!StringUtils.isEmpty(mailPassword)) {
            return mailPassword;
        }
        return System.getenv("YYGH_MAIL_PASSWORD");
    }

    private boolean sendSmsCode(String phone, String code) {
        DefaultProfile profile = DefaultProfile.
                getProfile(ConstantPropertiesUtils.REGION_Id,
                        ConstantPropertiesUtils.ACCESS_KEY_ID,
                        ConstantPropertiesUtils.SECRECT);
        IAcsClient client = new DefaultAcsClient(profile);
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");

        request.putQueryParameter("PhoneNumbers", phone);
        request.putQueryParameter("SignName", "我的谷粒在线教育网站");
        request.putQueryParameter("TemplateCode", "SMS_180051135");
        Map<String,Object> param = new HashMap();
        param.put("code",code);
        request.putQueryParameter("TemplateParam", JSONObject.toJSONString(param));

        try {
            CommonResponse response = client.getCommonResponse(request);
            log.debug("SMS provider response received for {}", phone);
            return response.getHttpResponse().isSuccess();
        } catch (ServerException e) {
            log.error("SMS send server exception for {}", phone, e);
        } catch (ClientException e) {
            log.error("SMS send client exception for {}", phone, e);
        }
        return false;
    }

    private boolean send(String phone, Map<String,Object> param) {
        if(StringUtils.isEmpty(phone)) {
            return false;
        }

        DefaultProfile profile = DefaultProfile.
                getProfile(ConstantPropertiesUtils.REGION_Id,
                        ConstantPropertiesUtils.ACCESS_KEY_ID,
                        ConstantPropertiesUtils.SECRECT);
        IAcsClient client = new DefaultAcsClient(profile);
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");

        request.putQueryParameter("PhoneNumbers", phone);
        request.putQueryParameter("SignName", "我的谷粒在线教育网站");
        request.putQueryParameter("TemplateCode", "SMS_180051135");
        request.putQueryParameter("TemplateParam", JSONObject.toJSONString(param));

        try {
            CommonResponse response = client.getCommonResponse(request);
            log.debug("SMS provider response received for {}", phone);
            return response.getHttpResponse().isSuccess();
        } catch (ServerException e) {
            log.error("SMS send server exception for {}", phone, e);
        } catch (ClientException e) {
            log.error("SMS send client exception for {}", phone, e);
        }
        return false;
    }
}
