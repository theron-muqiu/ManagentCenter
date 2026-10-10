package com.muqiu.managentcenter.system.dto;
import java.util.List;
import jakarta.validation.constraints.*;
/** 用户角色整体替换字段。 */
public record UserRolesRequest(
        /** 共享版本。 */ @NotNull @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version,
        /** 目标集合，空数组撤权。 */ @NotNull List<@NotNull @Pattern(regexp="[1-9][0-9]*") @Size(max=19) String> roleIds) {}
