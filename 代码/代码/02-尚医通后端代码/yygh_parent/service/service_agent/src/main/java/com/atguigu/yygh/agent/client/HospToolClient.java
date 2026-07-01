package com.atguigu.yygh.agent.client;

import com.atguigu.yygh.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "service-hosp")
public interface HospToolClient {

    @GetMapping("/api/hosp/hospital/findHospList/{page}/{limit}")
    Result<Object> searchHospitals(@PathVariable("page") Integer page,
                                   @PathVariable("limit") Integer limit,
                                   @RequestParam(value = "hosname", required = false) String hosname,
                                   @RequestParam(value = "hostype", required = false) String hostype,
                                   @RequestParam(value = "districtCode", required = false) String districtCode);

    @GetMapping("/api/hosp/hospital/findByHosName/{hosname}")
    Result<Object> findByHosName(@PathVariable("hosname") String hosname);

    @GetMapping("/api/hosp/hospital/findHospDetail/{hoscode}")
    Result<Object> getHospitalDetail(@PathVariable("hoscode") String hoscode);

    @GetMapping("/api/hosp/hospital/department/{hoscode}")
    Result<Object> listDepartments(@PathVariable("hoscode") String hoscode);

    @GetMapping("/api/hosp/hospital/auth/getBookingScheduleRule/{page}/{limit}/{hoscode}/{depcode}")
    Result<Object> findScheduleRules(@PathVariable("page") Integer page,
                                     @PathVariable("limit") Integer limit,
                                     @PathVariable("hoscode") String hoscode,
                                     @PathVariable("depcode") String depcode);

    @GetMapping("/api/hosp/hospital/auth/findScheduleList/{hoscode}/{depcode}/{workDate}")
    Result<Object> findScheduleList(@PathVariable("hoscode") String hoscode,
                                    @PathVariable("depcode") String depcode,
                                    @PathVariable("workDate") String workDate);

    @GetMapping("/api/hosp/hospital/getSchedule/{scheduleId}")
    Result<Object> getSchedule(@PathVariable("scheduleId") String scheduleId);
}
