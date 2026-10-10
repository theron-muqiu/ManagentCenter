package com.muqiu.managentcenter.system.dto;

import jakarta.validation.constraints.*;

/** 独立启停字段。 */
public record StatusRequest(
        /** 并发版本。 */ @NotNull @Pattern(regexp="0|[1-9][0-9]*") @Size(max=19) String version,
        /** 0停用、1启用。 */ @NotNull @Min(0) @Max(1) Integer status) {}
