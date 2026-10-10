package com.muqiu.managentcenter.system.dto;

import jakarta.validation.constraints.*;

/** 可编辑资料与共享修订号。 */
public record UpdateRoleRequest(
        /** 共享版本。 */ @NotNull @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version,
        /** 精确唯一名称。 */ @NotBlank @Size(max=100) String name,
        /** 可选说明，省略清空。 */ @Size(max=500) String description) {}
