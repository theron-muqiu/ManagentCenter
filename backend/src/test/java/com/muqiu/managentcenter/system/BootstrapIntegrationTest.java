package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.config.BootstrapProperties;
import com.muqiu.managentcenter.system.constants.PermissionDefinitions;
import com.muqiu.managentcenter.system.init.BootstrapInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 专用真实数据库验证初始化不覆盖密码、多实例及配置失败回滚。 */
class BootstrapIntegrationTest {
    /** 专用数据源。 */
    private static DriverManagerDataSource source;
    /** 数据库读取。 */
    private static JdbcTemplate jdbc;

    /** 初始化专用数据库迁移。 */
    @BeforeAll
    static void setup() {
        String url = System.getenv("DB_URL");
        if (url == null || !url.contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
        source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    /** 重复初始化不覆盖摘要，所有权限含两类日志查看。 */
    @Test
    void initializesAndPreservesAdministrator() {
        var encoder = new BCryptPasswordEncoder();
        var initializer = new BootstrapInitializer(jdbc, new DataSourceTransactionManager(source), encoder,
                new BootstrapProperties(System.getenv("BOOTSTRAP_USERNAME"), System.getenv("BOOTSTRAP_PASSWORD")));
        initializer.run(null);
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE is_super_admin=1", String.class);
        new BootstrapInitializer(jdbc, new DataSourceTransactionManager(source), encoder,
                new BootstrapProperties("", "")).run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE is_super_admin=1", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE is_super_admin=1", String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class)).isEqualTo(PermissionDefinitions.ALL.size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE code IN ('operation-log:query','login-log:query')", Integer.class)).isEqualTo(2);
    }

    /** 多实例同时初始化仍只有一个管理员。 */
    @Test
    void concurrentInitializationIsSerialized() throws Exception {
        var initializer = new BootstrapInitializer(jdbc, new DataSourceTransactionManager(source), new BCryptPasswordEncoder(),
                new BootstrapProperties(System.getenv("BOOTSTRAP_USERNAME"), System.getenv("BOOTSTRAP_PASSWORD")));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> initializer.run(null));
            var second = executor.submit(() -> initializer.run(null));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE is_super_admin=1", Long.class)).isEqualTo(1);
    }

    /** 首次无有效配置时权限与管理员一同回滚。 */
    @Test
    void invalidFirstConfigurationRollsBack() {
        jdbc.execute("CREATE DATABASE IF NOT EXISTS rbac_bootstrap_test");
        var isolated = new DriverManagerDataSource(System.getenv("DB_URL").replace("/rbac_test?", "/rbac_bootstrap_test?"),
                System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        try {
            Flyway.configure().dataSource(isolated).load().migrate();
            var isolatedJdbc = new JdbcTemplate(isolated);
            var initializer = new BootstrapInitializer(isolatedJdbc, new DataSourceTransactionManager(isolated),
                    new BCryptPasswordEncoder(), new BootstrapProperties("", ""));
            assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class);
            assertThat(isolatedJdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isZero();
            assertThat(isolatedJdbc.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP DATABASE rbac_bootstrap_test");
        }
    }
}
