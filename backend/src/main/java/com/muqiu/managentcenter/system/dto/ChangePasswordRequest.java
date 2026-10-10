package com.muqiu.managentcenter.system.dto;

import com.muqiu.managentcenter.common.ValidPassword;


/** 仅本人密码字段，不接受目标用户标识。 */
public record ChangePasswordRequest(
        /** 原密码，禁止记录。 */ @ValidPassword String oldPassword,
        /** 新密码，禁止记录。 */ @ValidPassword String newPassword) {
    /** 避免默认 record 输出密码。 */
    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}
