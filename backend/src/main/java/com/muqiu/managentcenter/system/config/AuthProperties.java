package com.muqiu.managentcenter.system.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 认证期限必须显式配置，不提供隐藏业务默认值。 */
@Validated
@ConfigurationProperties("auth")
public record AuthProperties(
        /** Token 绝对有效期限。 */
        @NotNull Duration tokenTtl) {

    /** 拒绝零、负数、溢出及无法生成有效毫秒 TTL 的配置。 */
    @AssertTrue(message = "auth.token-ttl 必须为正毫秒期限")
    public boolean isPositiveTtl() {
        if (tokenTtl == null) return false;
        try {
            return tokenTtl.toMillis() > 0;
        } catch (ArithmeticException exception) {
            return false;
        }
    }
}
