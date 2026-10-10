package com.muqiu.managentcenter.system.repository;

import com.muqiu.managentcenter.system.entity.PermissionRow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 预置权限及即时授权查询，不使用跨请求缓存。 */
@Repository
public class PermissionRepository {
    /** 数据库访问。 */
    private final JdbcTemplate jdbc;

    /** 注入数据库访问。 */
    public PermissionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 按预置顺序读完整权限树数据。 */
    public List<PermissionRow> listOrdered() {
        return jdbc.query("SELECT id,code,name,type,parent_id,route_path,sort_order FROM sys_permission ORDER BY sort_order,id",
                (row, index) -> new PermissionRow(row.getLong("id"), row.getString("code"), row.getString("name"),
                        row.getInt("type"), row.getObject("parent_id", Long.class), row.getString("route_path"), row.getInt("sort_order")));
    }

    /** 每次请求重新计算多角色权限并集，超级管理员获取全部预置权限。 */
    public Set<String> effectiveCodes(long userId, boolean superAdmin) {
        List<String> codes = superAdmin
                ? jdbc.queryForList("SELECT code FROM sys_permission ORDER BY code", String.class)
                : jdbc.queryForList("SELECT DISTINCT p.code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=? ORDER BY p.code",
                        String.class, userId);
        return new LinkedHashSet<>(codes);
    }
}
