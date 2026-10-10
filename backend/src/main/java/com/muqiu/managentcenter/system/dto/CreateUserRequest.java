package com.muqiu.managentcenter.system.dto;

import com.muqiu.managentcenter.common.ValidPassword;

import jakarta.validation.constraints.*;

/** 普通账号新增字段白名单。 */
public record CreateUserRequest(
        /** 不可变大小写敏感账号。 */ @NotBlank @Size(max=64) String username,
        /** 必填显示名称。 */ @NotBlank @Size(max=100) String displayName,
        /** 初始密码，禁止记录。 */ @ValidPassword String password,
        /** 可选联系手机号。 */ @Pattern(regexp="\\+?[0-9]{6,15}") String phone,
        /** 可选联系邮箱。 */ @Email @Size(max=254) String email) {
    /** 联系资料裁剪空白并归一 NULL，不修改账号。 */
    public CreateUserRequest {
        phone = phone == null || phone.isBlank() ? null : phone.strip();
        email = email == null || email.isBlank() ? null : email.strip();
    }
    /** 不打印密码及联系资料。 */
    @Override public String toString() { return "CreateUserRequest[redacted]"; }
}
