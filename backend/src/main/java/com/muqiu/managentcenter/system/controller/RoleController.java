package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.dto.RolePermissionsRequest;
import com.muqiu.managentcenter.system.dto.UpdateRoleRequest;
import com.muqiu.managentcenter.system.service.RoleService;
import com.muqiu.managentcenter.system.vo.RolePermissions;
import com.muqiu.managentcenter.system.vo.RoleView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/** 独立角色操作接口。 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {
    /** 入口日志。 */ private static final Logger log = LoggerFactory.getLogger(RoleController.class);
    /** 角色业务。 */ private final RoleService service;
    /** 注入角色业务。 */ public RoleController(RoleService service) { this.service = service; }
    /** 分页查询角色，记录筛选是否存在，不输出原文。 */
    @GetMapping
    public ApiResponse<PageData<RoleView>> list(@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @RequestParam(required=false) @Size(max=100) String keyword) {
        log.info("[角色管理] 查询角色 page={} size={} hasKeyword={}", page, size, keyword != null && !keyword.isEmpty());
        return ApiResponse.success(service.list(page, size, keyword));
    }
    /** 创建空权限角色。 */
    @PostMapping
    public ApiResponse<RoleView> create(@Valid @RequestBody CreateRoleRequest request) {
        log.info("[角色管理] 新增角色 name={}", request.name().replaceAll("[\\r\\n\\t]", ""));
        return ApiResponse.success(service.create(request));
    }
    /** 查询资料及授权摘要。 */
    @GetMapping("/{id}")
    public ApiResponse<RoleView> detail(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[角色管理] 查询详情 roleId={}", id);
        return ApiResponse.success(service.detail(Long.parseLong(id)));
    }
    /** 更新名称与说明，不接受权限字段。 */
    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody UpdateRoleRequest request) {
        log.info("[角色管理] 编辑资料 roleId={} version={}", id, request.version());
        return ApiResponse.success(service.update(Long.parseLong(id), request));
    }
    /** 删除无用户关联的角色。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id,
            @RequestParam @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version) {
        log.info("[角色管理] 删除角色 roleId={} version={}", id, version);
        service.delete(Long.parseLong(id), version);
        return ApiResponse.success(null);
    }
    /** 查询权限专用集合。 */
    @GetMapping("/{id}/permissions")
    public ApiResponse<RolePermissions> permissions(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id) {
        log.info("[角色管理] 查询权限集合 roleId={}", id);
        return ApiResponse.success(service.permissions(Long.parseLong(id)));
    }
    /** 整体替换权限集合。 */
    @PutMapping("/{id}/permissions")
    public ApiResponse<RolePermissions> assignPermissions(@PathVariable @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String id, @Valid @RequestBody RolePermissionsRequest request) {
        log.info("[角色管理] 分配权限 roleId={} permissionCount={} version={}", id, request.permissionIds().size(), request.version());
        return ApiResponse.success(service.assignPermissions(Long.parseLong(id), request));
    }
}
