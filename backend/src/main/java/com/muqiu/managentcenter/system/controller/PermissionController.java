package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.system.service.PermissionService;
import com.muqiu.managentcenter.system.vo.PermissionNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
/** 只读预置树，不提供运行时编辑接口。 */
@RestController
public class PermissionController {
    /** 入口日志。 */ private static final Logger log = LoggerFactory.getLogger(PermissionController.class);
    /** 树组织。 */ private final PermissionService service;
    /** 注入权限组织。 */ public PermissionController(PermissionService service) { this.service = service; }
    /** 返回完整预置选项，只有授权权限可访问。 */
    @GetMapping("/api/permissions/tree")
    public ApiResponse<List<PermissionNode>> tree(@org.springframework.security.core.annotation.AuthenticationPrincipal com.muqiu.managentcenter.system.security.AuthPrincipal principal) {
        log.info("[角色授权] 查询预置权限树 operatorId={}", principal.user().id());
        return ApiResponse.success(service.tree());
    }
}
