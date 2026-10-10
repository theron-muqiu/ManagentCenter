package com.muqiu.managentcenter.system.vo;

import java.time.Instant;
import java.util.List;

/** 对外角色资料及已授权代码。 */
public record RoleView(
        /** 角色 ID。 */ String id,
        /** 唯一名称。 */ String name,
        /** 可选说明。 */ String description,
        /** 并发版本。 */ String version,
        /** UTC 创建时间。 */ Instant createdAt,
        /** UTC 修改时间。 */ Instant updatedAt,
        /** 去重授权代码。 */ List<String> permissionCodes) {}
