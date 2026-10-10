package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.dto.RolePermissionsRequest;
import com.muqiu.managentcenter.system.dto.UpdateRoleRequest;
import com.muqiu.managentcenter.system.repository.RoleRepository;
import com.muqiu.managentcenter.system.vo.RoleOption;
import com.muqiu.managentcenter.system.vo.RolePermissions;
import com.muqiu.managentcenter.system.vo.RoleView;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色资料与关联删除保护。 */
@Service
public class RoleService {
    /** 业务节点日志。 */ private static final Logger log = LoggerFactory.getLogger(RoleService.class);
    /** 数据访问。 */ private final JdbcTemplate jdbc;
    /** 行锁与视图。 */ private final RoleRepository roles;
    /** 注入角色业务依赖。 */ public RoleService(JdbcTemplate jdbc, RoleRepository roles) { this.jdbc = jdbc; this.roles = roles; }
    /** 参数化字面搜索，角色及总数在同一只读快照内。 */
    @Transactional(readOnly=true)
    public PageData<RoleView> list(int page, int size, String keyword) {
        String where = "";
        var args = new ArrayList<Object>();
        if (keyword != null && !keyword.isEmpty()) {
            where = " WHERE name LIKE ? ESCAPE '!'";
            args.add("%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role" + where, Long.class, args.toArray());
        args.add(size); args.add(((long) page - 1) * size);
        var ids = jdbc.queryForList("SELECT id FROM sys_role" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", Long.class, args.toArray());
        return new PageData<>(ids.stream().map(roles::view).toList(), total, page, size);
    }
    /** 一致快照读取资料与授权摘要。 */
    @Transactional(readOnly=true)
    public RoleView detail(long id) { return roles.view(id); }
    /** 新增空权限角色，精确唯一键保护并发创建。 */
    @Transactional
    public RoleView create(CreateRoleRequest request) {
        if (!request.name().equals(request.name().strip())) throw new ApiException(ErrorCode.INVALID_INPUT);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO sys_role(name,description,version,created_at,updated_at) VALUES(?,?,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.name()); statement.setString(2, request.description()); return statement;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        log.info("[角色管理] 新增角色待提交 roleId={}", id);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return roles.view(id);
    }
    /** 行锁和共享版本避免资料更新覆盖授权变化。 */
    @Transactional
    public RoleView update(long id, UpdateRoleRequest request) {
        if (!request.name().equals(request.name().strip())) throw new ApiException(ErrorCode.INVALID_INPUT);
        long version = Long.parseLong(request.version());
        if (roles.lockVersion(id) != version) throw new ApiException(ErrorCode.CONFLICT);
        if (jdbc.update("UPDATE sys_role SET name=?,description=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?", request.name(), request.description(), id, version) != 1)
            throw new ApiException(ErrorCode.CONFLICT);
        log.info("[角色管理] 资料更新待提交 roleId={} version={}", id, version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return roles.view(id);
    }
    /** 角色锁内检查全部关联，包括停用用户；先删除权限关联。 */
    @Transactional
    public void delete(long id, String requestedVersion) {
        long version = Long.parseLong(requestedVersion);
        if (roles.lockVersion(id) != version) throw new ApiException(ErrorCode.CONFLICT);
        // 锁定读避免事务已有快照遗漏刚提交的用户关联。
        var associations = jdbc.queryForList("SELECT user_id FROM sys_user_role WHERE role_id=? FOR UPDATE", Long.class, id);
        if (!associations.isEmpty()) throw new ApiException(ErrorCode.ROLE_IN_USE);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
        jdbc.update("DELETE FROM sys_role WHERE id=? AND version=?", id, version);
        log.info("[角色管理] 删除角色待提交 roleId={} version={}", id, version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
    }

    /** 读取完整授权集合及共享版本，不要求角色查询权限。 */
    @Transactional(readOnly=true)
    public RolePermissions permissions(long id) {
        RoleView role = roles.view(id);
        var ids = jdbc.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id=? ORDER BY permission_id", Long.class, id);
        return new RolePermissions(Long.toString(id), role.version(), ids.stream().map(Object::toString).toList());
    }

    /** 锁角色验证全部目标后整体替换，不自动继承父子权限。 */
    @Transactional
    public RolePermissions assignPermissions(long id, RolePermissionsRequest request) {
        long version = Long.parseLong(request.version());
        var desired = new java.util.TreeSet<Long>();
        for (String permissionId : request.permissionIds()) desired.add(Long.parseLong(permissionId));
        if (roles.lockVersion(id) != version) throw new ApiException(ErrorCode.CONFLICT);
        var valid = new java.util.HashSet<>(jdbc.queryForList("SELECT id FROM sys_permission", Long.class));
        if (!valid.containsAll(desired)) throw new ApiException(ErrorCode.NOT_FOUND);
        var previous = new java.util.TreeSet<>(jdbc.queryForList("SELECT permission_id FROM sys_role_permission WHERE role_id=? ORDER BY permission_id", Long.class, id));
        if (previous.equals(desired)) {
            com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
            return new RolePermissions(Long.toString(id), Long.toString(version), desired.stream().map(Object::toString).toList());
        }
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
        for (long permissionId : desired) jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,created_at) VALUES(?,?,UTC_TIMESTAMP(3))", id, permissionId);
        if (jdbc.update("UPDATE sys_role SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?", id, version) != 1)
            throw new ApiException(ErrorCode.CONFLICT);
        log.info("[角色管理] 权限授权待提交 roleId={} permissionCount={} version={}", id, desired.size(), version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return new RolePermissions(Long.toString(id), Long.toString(version + 1), desired.stream().map(Object::toString).toList());
    }

    /** 提供授权专用选项，不附加角色查询权限要求。 */
    @Transactional(readOnly=true)
    public java.util.List<RoleOption> options() {
        return jdbc.query("SELECT id,name FROM sys_role ORDER BY id", (row, index) -> new RoleOption(Long.toString(row.getLong("id")), row.getString("name")));
    }
}
