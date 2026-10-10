package com.muqiu.managentcenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** 基础后台启动入口。 */
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ManagentCenterApplication {
    /** 启动应用并验证必要配置。 */
    public static void main(String[] args) {
        SpringApplication.run(ManagentCenterApplication.class, args);
    }
}
