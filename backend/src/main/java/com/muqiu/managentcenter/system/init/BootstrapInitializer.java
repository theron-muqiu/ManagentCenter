package com.muqiu.managentcenter.system.init;

import com.muqiu.managentcenter.common.PasswordValidator;
import com.muqiu.managentcenter.system.config.BootstrapProperties;
import com.muqiu.managentcenter.system.constants.PermissionDefinitions;
import com.muqiu.managentcenter.system.enums.UserStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用同连接命名锁及事务幂等初始化权限和唯一超级管理员。 */
@Component
public class BootstrapInitializer implements ApplicationRunner {
    /** 运行日志。 */
    private static final Logger log = LoggerFactory.getLogger(BootstrapInitializer.class);
    /** 事务数据库访问。 */
    private final JdbcTemplate jdbc;
    /** 初始化事务。 */
    private final TransactionTemplate transactions;
    /** 密码编码器。 */
    private final PasswordEncoder encoder;
    /** 首次部署配置。 */
    private final BootstrapProperties properties;

    /** 注入初始化所需组件，不读取/输出不相关环境。 */
    public BootstrapInitializer(JdbcTemplate jdbc, PlatformTransactionManager manager,
            PasswordEncoder encoder, BootstrapProperties properties) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.encoder = encoder;
        this.properties = properties;
    }

    /** 启动时初始化；失败传播给启动器，不能伪称就绪。 */
    @Override
    public void run(ApplicationArguments args) {
        log.info("[系统初始化] 开始检查权限和管理员");
        transactions.executeWithoutResult(status -> {
            Connection connection = DataSourceUtils.getConnection(jdbc.getDataSource());
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK('muqiu:rbac:bootstrap',10)");
                    ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("初始化锁不可用");
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException("初始化锁失败");
            }
            // 命名锁必须在提交/回滚之后释放，避免其他实例读到未提交的管理员状态。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 释放同一连接持有的命名锁。 */
                @Override
                public void afterCompletion(int completionStatus) {
                    try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK('muqiu:rbac:bootstrap')")) {
                        release.execute();
                    } catch (java.sql.SQLException exception) {
                        log.error("[系统初始化] 释放初始化锁失败");
                    }
                }
            });
            HashSet<String> seen = new HashSet<>();
            for (var permission : PermissionDefinitions.ALL) {
                if (!seen.add(permission.code()) || (permission.parentCode() != null && !seen.contains(permission.parentCode()))) {
                    throw new IllegalStateException("预置权限代码重复或父树无效");
                }
                Long parent = permission.parentCode() == null ? null :
                        jdbc.queryForObject("SELECT id FROM sys_permission WHERE code=?", Long.class, permission.parentCode());
                jdbc.update("INSERT INTO sys_permission(code,name,type,parent_id,route_path,sort_order,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE code=code",
                        permission.code(), permission.name(), permission.type(), parent, permission.routePath(), permission.sortOrder());
            }
            Long admins = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE is_super_admin=1", Long.class);
            if (admins == 0) {
                String username = properties.username();
                if (username == null || username.isBlank() || username.length() > 64 || !username.equals(username.strip())
                        || !new PasswordValidator().isValid(properties.password(), null)) {
                    throw new IllegalStateException("首次管理员账号/密码配置无效");
                }
                jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,created_at,updated_at) VALUES(?,?,?,?,1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                        username, "超级管理员", encoder.encode(properties.password()), UserStatus.ENABLED.code());
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 仅提交之后报告完成，不输出账号密码。 */
                @Override
                public void afterCommit() {
                    log.info("[系统初始化] 权限和管理员准备完成 permissionCount={}", PermissionDefinitions.ALL.size());
                }
            });
        });
    }
}
