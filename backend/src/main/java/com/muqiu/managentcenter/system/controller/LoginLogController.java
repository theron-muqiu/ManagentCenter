package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.service.LoginLogService;
import com.muqiu.managentcenter.system.vo.LoginLogDetail;
import com.muqiu.managentcenter.system.vo.LoginLogListItem;

import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** 登录历史只读接口，独立 login-log:query 权限。 */
@RestController
@RequestMapping("/api/login-logs")
public class LoginLogController {
    /** 安全入口日志。 */ private static final Logger log = LoggerFactory.getLogger(LoginLogController.class);
    /** 只读业务。 */ private final LoginLogService service;
    /** 注入只读业务。 */ public LoginLogController(LoginLogService service) { this.service = service; }
    /** 精确筛选和分页，不递归采集查询事件。 */
    @GetMapping
    public ApiResponse<PageData<LoginLogListItem>> list(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @RequestParam(required=false) @Size(max=64) String attemptedUsername,
            @RequestParam(required=false) @Min(0) @Max(1) Integer result, @RequestParam(required=false) String startTime, @RequestParam(required=false) String endTime) {
        log.info("[登录日志] 查询记录 page={} size={} result={} hasUsername={} hasStart={} hasEnd={}", page, size, result, attemptedUsername != null, startTime != null, endTime != null);
        return ApiResponse.success(service.list(page, size, attemptedUsername, result, startTime, endTime));
    }
    /** 返回历史快照，不依赖当前用户存在。 */
    @GetMapping("/{id}")
    public ApiResponse<LoginLogDetail> detail(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[登录日志] 查询详情 logId={}", id);
        return ApiResponse.success(service.detail(Long.parseLong(id)));
    }
}
