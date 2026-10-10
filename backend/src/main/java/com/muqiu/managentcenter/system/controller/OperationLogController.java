package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.service.OperationLogService;
import com.muqiu.managentcenter.system.vo.OperationLogDetail;
import com.muqiu.managentcenter.system.vo.OperationLogListItem;

import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** 操作记录只读接口，独立 operation-log:query 权限。 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {
    /** 安全入口日志。 */ private static final Logger log = LoggerFactory.getLogger(OperationLogController.class);
    /** 只读业务。 */ private final OperationLogService service;
    /** 注入只读业务。 */ public OperationLogController(OperationLogService service) { this.service = service; }
    /** 分页查询历史，不记录筛选账号原文或形成审计递归。 */
    @GetMapping
    public ApiResponse<PageData<OperationLogListItem>> list(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @RequestParam(required=false) @Size(max=64) String operatorUsername,
            @RequestParam(required=false) @Min(0) @Max(7) Integer operationType, @RequestParam(required=false) @Min(0) @Max(1) Integer result,
            @RequestParam(required=false) String startTime, @RequestParam(required=false) String endTime) {
        log.info("[操作记录] 查询记录 page={} size={} operationType={} result={} hasUsername={} hasStart={} hasEnd={}", page, size, operationType, result, operatorUsername != null, startTime != null, endTime != null);
        return ApiResponse.success(service.list(page, size, operatorUsername, operationType, result, startTime, endTime));
    }
    /** 只读查看历史快照。 */
    @GetMapping("/{id}")
    public ApiResponse<OperationLogDetail> detail(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[操作记录] 查询详情 logId={}", id);
        return ApiResponse.success(service.detail(Long.parseLong(id)));
    }
}
