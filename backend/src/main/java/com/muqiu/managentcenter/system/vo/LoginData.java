package com.muqiu.managentcenter.system.vo;

import java.time.Instant;

/** LoginData 明确字段契约。 */
public record LoginData(
        /** 用户公开资料。 */ UserView user,
        /** Token 绝对过期时间。 */ Instant expiresAt,
        /** 本次签发凭据，禁止记录。 */ String token,
        /** 固定 Bearer。 */ String tokenType
) {
    /** 不允许默认字符串表示泄漏登录凭据。 */
    @Override public String toString() { return "LoginData[redacted]"; }
}
