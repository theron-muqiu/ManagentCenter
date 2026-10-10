package com.muqiu.managentcenter.system.vo;
import java.util.List;
/** 用户完整角色集合及共享版本。 */
public record UserRoles(
        /** 用户标识。 */ String userId,
        /** 共享修订号。 */ String version,
        /** 角色标识，升序。 */ List<String> roleIds) {}
