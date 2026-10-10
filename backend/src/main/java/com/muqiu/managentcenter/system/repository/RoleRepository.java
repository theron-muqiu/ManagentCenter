package com.muqiu.managentcenter.system.repository;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.vo.RoleView;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 角色锁与只读视图，不把权限集合存入主表。 */
@Repository
public class RoleRepository {
    /** 数据读取。 */ private final JdbcTemplate jdbc;
    /** 注入数据库访问。 */ public RoleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** 锁角色并返回共享版本，供资料、删除与授权竞争使用。 */
    public long lockVersion(long id) {
        var rows = jdbc.queryForList("SELECT version FROM sys_role WHERE id=? FOR UPDATE", Long.class, id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }
    /** 返回公开资料及独立授权代码集合。 */
    public RoleView view(long id) {
        var rows = jdbc.query("SELECT * FROM sys_role WHERE id=?", (row, index) -> new RoleView(
                Long.toString(row.getLong("id")), row.getString("name"), row.getString("description"), Long.toString(row.getLong("version")),
                row.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), row.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                java.util.List.of()), id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        RoleView role = rows.getFirst();
        var codes = jdbc.queryForList("SELECT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id WHERE rp.role_id=? ORDER BY p.code", String.class, id);
        return new RoleView(role.id(), role.name(), role.description(), role.version(), role.createdAt(), role.updatedAt(), codes);
    }
}
