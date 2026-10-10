package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.dto.ResetPasswordRequest;
import com.muqiu.managentcenter.system.dto.StatusRequest;
import com.muqiu.managentcenter.system.dto.UpdateUserRequest;
import com.muqiu.managentcenter.system.dto.UserRolesRequest;
import com.muqiu.managentcenter.system.service.UserService;
import com.muqiu.managentcenter.system.vo.UserRoles;
import com.muqiu.managentcenter.system.vo.UserView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** 用户管理接口，各操作由安全链独立授权。 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    /** 入口日志。 */ private static final Logger log = LoggerFactory.getLogger(UserController.class);
    /** 用户业务。 */ private final UserService service;
    /** 注入用户业务。 */ public UserController(UserService service) { this.service = service; }
    /** 分页查询，日志不保存搜索原文。 */
    @GetMapping
    public ApiResponse<PageData<UserView>> list(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @RequestParam(required=false) @Size(max=100) String keyword,
            @RequestParam(required=false) @Min(0) @Max(1) Integer status) {
        log.info("[用户管理] 查询用户 page={} size={} status={} hasKeyword={}", page, size, status, keyword != null && !keyword.isEmpty());
        return ApiResponse.success(service.list(page, size, keyword, status));
    }
    /** 新增普通账号，不接受状态或角色字段。 */
    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        log.info("[用户管理] 新增用户 username={}", request.username().replaceAll("[\\r\\n\\t]", ""));
        return ApiResponse.success(service.create(request));
    }
    /** 查询单个用户。 */
    @GetMapping("/{id}")
    public ApiResponse<UserView> detail(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[用户管理] 查询详情 userId={}", id);
        return ApiResponse.success(service.detail(Long.parseLong(id)));
    }
    /** 替换可编辑资料。 */
    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody UpdateUserRequest request) {
        log.info("[用户管理] 编辑资料 userId={} version={}", id, request.version());
        return ApiResponse.success(service.update(Long.parseLong(id), request));
    }
    /** 独立启停入口。 */
    @PutMapping("/{id}/status")
    public ApiResponse<UserView> changeStatus(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody StatusRequest request) {
        log.info("[用户管理] 调整状态 userId={} status={} version={}", id, request.status(), request.version());
        return ApiResponse.success(service.changeStatus(Long.parseLong(id), request));
    }
    /** 独立重置密码入口。 */
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody ResetPasswordRequest request) {
        log.info("[用户管理] 重置密码 userId={} version={}", id, request.version());
        service.resetPassword(Long.parseLong(id), request);
        return ApiResponse.success(null);
    }
    /** 查询授权专用角色集合。 */
    @GetMapping("/{id}/roles")
    public ApiResponse<UserRoles> roles(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[用户管理] 查询角色集合 userId={}", id);
        return ApiResponse.success(service.roles(Long.parseLong(id)));
    }
    /** 整体替换角色集合。 */
    @PutMapping("/{id}/roles")
    public ApiResponse<UserRoles> assignRoles(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody UserRolesRequest request) {
        log.info("[用户管理] 分配角色 userId={} roleCount={} version={}", id, request.roleIds().size(), request.version());
        return ApiResponse.success(service.assignRoles(Long.parseLong(id), request));
    }
}
