package com.muqiu.managentcenter.system.entity;

import java.time.Instant;

/** UserAccount 明确字段契约。 */
public record UserAccount(
        /** 用户 ID。 */ long id,
        /** 区分大小写的登录账号。 */ String username,
        /** 显示名称。 */ String displayName,
        /** 仅内部认证的密码摘要。 */ String passwordHash,
        /** 固定状态编码。 */ int status,
        /** 初始化管理员身份。 */ boolean superAdmin,
        /** 可选联系方式。 */ String phone,
        /** 可选邮箱。 */ String email,
        /** 资料修订号。 */ long version,
        /** 认证撤销版本。 */ long authVersion,
        /** UTC 创建时间。 */ Instant createdAt,
        /** UTC 修改时间。 */ Instant updatedAt
) {
    /** 内部实体禁止默认打印摘要与完整联系资料。 */
    @Override public String toString() { return "UserAccount[id=" + id + "]"; }
}
