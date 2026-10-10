package com.muqiu.managentcenter.system.dto;

import jakarta.validation.constraints.*;

/** 资料整体替换，不接收账号、状态或授权字段。 */
public record UpdateUserRequest(
        /** 并发版本。 */ @NotNull @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version,
        /** 必填显示名称。 */ @NotBlank @Size(max=100) String displayName,
        /** 可选联系手机号。 */ @Pattern(regexp="\\+?[0-9]{6,15}") String phone,
        /** 可选联系邮箱。 */ @Email @Size(max=254) String email) {
    /** 联系资料空值归一，不把联系方式当作唯一标识。 */
    public UpdateUserRequest {
        phone = phone == null || phone.isBlank() ? null : phone.strip();
        email = email == null || email.isBlank() ? null : email.strip();
    }
    /** 不打印完整联系资料。 */
    @Override public String toString() { return "UpdateUserRequest[redacted]"; }
}
