package com.muqiu.managentcenter.system.constants;

import java.util.List;

/** 固定权限树，父子仅作展示关系，不自动继承。 */
public final class PermissionDefinitions {
    /** 固定权限集合，父节点先于子节点。 */
    public static final List<Definition> ALL = List.of(
            new Definition("menu:system", "系统管理", 0, null, null, 0),
            new Definition("user:query", "用户管理", 1, "menu:system", "/system/users", 0),
            new Definition("user:create", "新增用户", 2, "user:query", null, 0),
            new Definition("user:edit", "编辑用户", 2, "user:query", null, 1),
            new Definition("user:status", "调整用户状态", 2, "user:query", null, 2),
            new Definition("user:reset-password", "重置用户密码", 2, "user:query", null, 3),
            new Definition("user:assign-role", "分配用户角色", 2, "user:query", null, 4),
            new Definition("role:query", "角色管理", 1, "menu:system", "/system/roles", 1),
            new Definition("role:create", "新增角色", 2, "role:query", null, 0),
            new Definition("role:edit", "编辑角色", 2, "role:query", null, 1),
            new Definition("role:delete", "删除角色", 2, "role:query", null, 2),
            new Definition("role:assign-permission", "分配角色权限", 2, "role:query", null, 3),
            new Definition("menu:audit", "日志管理", 0, null, null, 1),
            new Definition("operation-log:query", "操作记录", 1, "menu:audit", "/audit/operations", 0),
            new Definition("login-log:query", "登录日志", 1, "menu:audit", "/audit/logins", 1)
    );

    /** 仅静态定义，不允许实例化。 */
    private PermissionDefinitions() {}

    /** 预置权限数据。 */
    public record Definition(
            /** 稳定权限代码。 */ String code,
            /** 中文名称。 */ String name,
            /** 固定整型权限类型。 */ int type,
            /** 父权限代码。 */ String parentCode,
            /** 可访问的前端路径。 */ String routePath,
            /** 同级顺序。 */ int sortOrder) {}
}
