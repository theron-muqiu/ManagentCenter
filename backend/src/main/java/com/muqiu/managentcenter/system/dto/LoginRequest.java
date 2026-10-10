package com.muqiu.managentcenter.system.dto;

import com.muqiu.managentcenter.common.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LoginRequest 明确字段契约。 */
public record LoginRequest(
        /** 大小写敏感账号，首尾无空白。 */ @NotBlank @Size(max=64) String username,
        /** 密码，仅用于认证。 */ @ValidPassword String password
) {
    /** 不允许默认字符串表示泄漏密码。 */
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
