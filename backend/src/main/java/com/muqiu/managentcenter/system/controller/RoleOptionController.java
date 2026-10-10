package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.system.service.RoleService;
import com.muqiu.managentcenter.system.vo.RoleOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
/** 角色授权选项单独授权。 */
@RestController
public class RoleOptionController {
    /** 入口日志。 */ private static final Logger log = LoggerFactory.getLogger(RoleOptionController.class);
    /** 角色业务。 */ private final RoleService service;
    /** 注入角色业务。 */ public RoleOptionController(RoleService service) { this.service = service; }
    /** 返回可分配选项，不读取普通角色详情。 */
    @GetMapping("/api/role-options")
    public ApiResponse<List<RoleOption>> list(@org.springframework.security.core.annotation.AuthenticationPrincipal com.muqiu.managentcenter.system.security.AuthPrincipal principal) {
        log.info("[用户授权] 查询角色选项 operatorId={}", principal.user().id());
        return ApiResponse.success(service.options());
    }
}
