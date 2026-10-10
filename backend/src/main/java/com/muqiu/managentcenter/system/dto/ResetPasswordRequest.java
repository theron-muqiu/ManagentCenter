package com.muqiu.managentcenter.system.dto;

import com.muqiu.managentcenter.common.ValidPassword;

import jakarta.validation.constraints.*;

/** 管理员重置密码字段。 */
public record ResetPasswordRequest(
        /** 并发版本。 */ @NotNull @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version,
        /** 指定新密码。 */ @ValidPassword String newPassword) {
    /** 不打印重置密码。 */
    @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
}
