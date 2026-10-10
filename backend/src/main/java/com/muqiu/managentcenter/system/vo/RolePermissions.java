package com.muqiu.managentcenter.system.vo;
import java.util.List;
/** 角色完整权限集合及共享版本。 */
public record RolePermissions(
        /** 角色标识。 */ String roleId,
        /** 共享修订号。 */ String version,
        /** 权限标识，升序。 */ List<String> permissionIds) {}
