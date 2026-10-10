package com.muqiu.managentcenter.system.vo;

import java.time.Instant;
import java.util.List;

/** UserView 明确字段契约。 */
public record UserView(
        /** 用户 ID 字符串。 */ String id,
        /** 不可修改的账号。 */ String username,
        /** 显示名称。 */ String displayName,
        /** 可选手机号。 */ String phone,
        /** 可选邮箱。 */ String email,
        /** 固定状态编码。 */ int status,
        /** 只读管理员身份。 */ boolean superAdmin,
        /** 资料修订号字符串。 */ String version,
        /** UTC 创建时间。 */ Instant createdAt,
        /** UTC 修改时间。 */ Instant updatedAt,
        /** 关联角色简要资料。 */ List<RoleOption> roles
) {}
