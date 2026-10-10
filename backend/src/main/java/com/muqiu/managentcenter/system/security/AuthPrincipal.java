package com.muqiu.managentcenter.system.security;

import com.muqiu.managentcenter.system.entity.UserAccount;

import java.time.Instant;
import java.util.Set;

/** 当前请求身份，不跨请求缓存。 */
public record AuthPrincipal(
        /** 当前内部用户。 */ UserAccount user,
        /** 当前有效权限并集。 */ Set<String> codes,
        /** 当前原始凭据，仅用于退出。 */ String token,
        /** Token 过期时间。 */ Instant expiresAt) {
    /** 不允许默认打印身份内的密码摘要和 Token。 */
    @Override public String toString() { return "AuthPrincipal[userId=" + user.id() + "]"; }
}
