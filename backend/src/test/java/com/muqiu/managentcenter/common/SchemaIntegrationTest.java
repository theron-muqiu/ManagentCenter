package com.muqiu.managentcenter.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 使用专用真实 MySQL 验证迁移和数据库约束，禁止连接业务数据库。 */
class SchemaIntegrationTest {
    /** 专用测试数据库访问。 */
    private static JdbcTemplate jdbc;

    /** 显式连接专用环境，重复应用 Flyway 验证幂等。 */
    @BeforeAll
    static void migrateTestSchema() {
        String url = System.getenv("DB_URL");
        if (url == null || !url.contains("/rbac_test")) {
            throw new IllegalStateException("请配置专用 rbac_test 数据库环境，不允许使用业务数据库");
        }
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        Flyway flyway = Flyway.configure().dataSource(source).load();
        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        jdbc = new JdbcTemplate(source);
    }

    /** 基础版只有七张业务和日志表，不创建登录凭据表。 */
    @Test
    void expectedTablesAndNoSessionTable() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'sys_%'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_session'", Integer.class)).isZero();
    }

    /** 账号大小写敏感唯一，联系资料不唯一，CHECK 阻止无效状态。 */
    @Test
    void caseSensitiveAccountsAndSharedContacts() {
        String name = "Case" + UUID.randomUUID().toString().replace("-", "");
        String insert = "INSERT INTO sys_user(username,display_name,password_hash,phone,email,created_at,updated_at) VALUES(?,'测试','test-hash','123456789','shared@example.com',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))";
        try {
            jdbc.update(insert, name);
            jdbc.update(insert, name.toLowerCase());
            assertThatThrownBy(() -> jdbc.update(insert, name)).isInstanceOf(DuplicateKeyException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username IN (?,?)", Integer.class, name, name.toLowerCase())).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("UPDATE sys_user SET status=2 WHERE username=?", name))
                    .rootCause().isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getErrorCode()).isEqualTo(3819));
            assertThatThrownBy(() -> jdbc.update("UPDATE sys_user SET is_super_admin=1,status=0 WHERE username=?", name))
                    .rootCause().isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getErrorCode()).isEqualTo(3819));
        } finally {
            jdbc.update("DELETE FROM sys_user WHERE username IN (?,?)", name, name.toLowerCase());
        }
    }

    /** 外键拒绝悬空角色关联，日志历史字段不强制引用业务对象。 */
    @Test
    void rejectsDanglingRoleAssignments() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sys_user_role(user_id,role_id,created_at) VALUES(9223372036854775806,9223372036854775806,UTC_TIMESTAMP(3))")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('sys_operation_log','sys_login_log')", Integer.class)).isZero();
    }
}
