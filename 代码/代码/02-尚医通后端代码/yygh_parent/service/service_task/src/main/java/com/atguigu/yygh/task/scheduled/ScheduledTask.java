package com.atguigu.yygh.task.scheduled;

import com.atguigu.common.rabbit.constant.MqConst;
import com.atguigu.common.rabbit.service.RabbitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@EnableScheduling
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;

    @Value("${yygh.task.patient-tips.enabled:false}")
    private boolean patientTipsEnabled;

    //每天8点执行方法，就医提醒
    @Scheduled(cron = "${yygh.task.patient-tips.cron:0 0 8 * * ?}")
    public void taskPatient() {
        if (!patientTipsEnabled) {
            return;
        }
        rabbitService.sendMessage(
                MqConst.EXCHANGE_DIRECT_TASK,
                MqConst.ROUTING_TASK_8,
                Collections.singletonMap("type", "patientTips"));
    }
}
