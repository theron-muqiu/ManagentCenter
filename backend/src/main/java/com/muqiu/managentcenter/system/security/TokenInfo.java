package com.muqiu.managentcenter.system.security;

import java.time.Instant;

/** TokenInfo 明确字段契约。 */
public record TokenInfo(
        /** 签发用户 ID。 */ String userId,
        /** 签发认证版本。 */ String authVersion,
        /** 签发时间。 */ Instant createdAt,
        /** 绝对过期时间。 */ Instant expiresAt
) {}
