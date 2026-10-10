package com.muqiu.managentcenter.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 首次管理员配置，已有管理员时不覆盖其密码。 */
@ConfigurationProperties("bootstrap")
public record BootstrapProperties(
        /** 首次初始化账号。 */
        String username,
        /** 首次初始化密码，禁止写日志。 */
        String password) {
}
