package com.muqiu.managentcenter.system.dto;

import jakarta.validation.constraints.*;

/** 初始权限为空的角色资料。 */
public record CreateRoleRequest(
        /** 精确唯一名称。 */ @NotBlank @Size(max=100) String name,
        /** 可选说明。 */ @Size(max=500) String description) {}
