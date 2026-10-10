package com.muqiu.managentcenter.system.vo;

import java.time.Instant;
import java.util.List;

/** MeData 明确字段契约。 */
public record MeData(
        /** 当前用户资料。 */ UserView user,
        /** 直接关联角色 ID。 */ List<String> roleIds,
        /** 去重最新有效权限。 */ List<String> permissionCodes,
        /** 可见页面与容器。 */ List<MenuNode> menus,
        /** 当前 Token 过期时间。 */ Instant expiresAt
) {}
