package com.muqiu.managentcenter.system.security;

/** 内部签发结果，不输出调试正文。 */
public record IssuedToken(
        /** 原始凭据，禁止记录。 */ String token,
        /** 安全登录信息。 */ TokenInfo info) {
    /** 调试打印也不得泄漏原始凭据。 */
    @Override public String toString() { return "IssuedToken[redacted]"; }
}
