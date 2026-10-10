package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.dto.ResetPasswordRequest;
import com.muqiu.managentcenter.system.dto.StatusRequest;
import com.muqiu.managentcenter.system.dto.UpdateUserRequest;
import com.muqiu.managentcenter.system.dto.UserRolesRequest;
import com.muqiu.managentcenter.system.entity.UserAccount;
import com.muqiu.managentcenter.system.enums.UserStatus;
import com.muqiu.managentcenter.system.repository.UserRepository;
import com.muqiu.managentcenter.system.vo.UserRoles;
import com.muqiu.managentcenter.system.vo.UserView;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户事务规则及不可变账号保护。 */
@Service
public class UserService {
    /** 业务节点日志。 */ private static final Logger log = LoggerFactory.getLogger(UserService.class);
    /** 数据访问。 */ private final JdbcTemplate jdbc;
    /** 用户行锁与公开视图。 */ private final UserRepository users;
    /** 密码编码。 */ private final PasswordEncoder encoder;
    /** 注入用户业务依赖。 */
    public UserService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder) { this.jdbc = jdbc; this.users = users; this.encoder = encoder; }

    /** 参数化字面包含查询，分页与总数共享事务快照。 */
    @Transactional(readOnly=true)
    public PageData<UserView> list(int page, int size, String keyword, Integer status) {
        String where = " WHERE 1=1";
        var args = new ArrayList<Object>();
        if (keyword != null && !keyword.isEmpty()) {
            String literal = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            where += " AND (username LIKE ? ESCAPE '!' OR display_name LIKE ? ESCAPE '!')"; args.add(literal); args.add(literal);
        }
        if (status != null) { where += " AND status=?"; args.add(status); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user" + where, Long.class, args.toArray());
        args.add(size); args.add(((long) page - 1) * size);
        var ids = jdbc.queryForList("SELECT id FROM sys_user" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", Long.class, args.toArray());
        return new PageData<>(ids.stream().map(users::view).toList(), total, page, size);
    }

    /** 一致快照读取公开资料。 */
    @Transactional(readOnly=true)
    public UserView detail(long id) { return users.view(id); }

    /** 只创建启用普通用户，无角色且数据库唯一键承接并发判重。 */
    @Transactional
    public UserView create(CreateUserRequest request) {
        if (!request.username().equals(request.username().strip())) throw new ApiException(ErrorCode.INVALID_INPUT);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,phone,email,version,auth_version,created_at,updated_at) VALUES(?,?,?,1,0,?,?,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.username()); statement.setString(2, request.displayName()); statement.setString(3, encoder.encode(request.password()));
            statement.setString(4, request.phone()); statement.setString(5, request.email()); return statement;
        }, key);
        long id = java.util.Objects.requireNonNull(key.getKey()).longValue();
        log.info("[用户管理] 新增用户待提交 userId={}", id);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return users.view(id);
    }

    /** 锁行校验版本后整体替换可编辑资料。 */
    @Transactional
    public UserView update(long id, UpdateUserRequest request) {
        long version = Long.parseLong(request.version());
        UserAccount current = users.lockById(id);
        if (current.version() != version) throw new ApiException(ErrorCode.CONFLICT);
        int changed = jdbc.update("UPDATE sys_user SET display_name=?,phone=?,email=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?",
                request.displayName(), request.phone(), request.email(), id, version);
        if (changed != 1) throw new ApiException(ErrorCode.CONFLICT);
        log.info("[用户管理] 资料更新待提交 userId={} version={}", id, version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return users.view(id);
    }

    /** 同状态幂等，启用转停用原子撤销全部已有凭据。 */
    @Transactional
    public UserView changeStatus(long id, StatusRequest request) {
        long version = Long.parseLong(request.version());
        UserAccount current = users.lockById(id);
        if (current.superAdmin() && request.status() == UserStatus.DISABLED.code()) throw new ApiException(ErrorCode.SUPER_ADMIN_PROTECTED);
        if (current.status() == request.status()) {
            com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
            return users.view(id);
        }
        if (current.version() != version) throw new ApiException(ErrorCode.CONFLICT);
        int revoke = request.status() == UserStatus.DISABLED.code() ? 1 : 0;
        jdbc.update("UPDATE sys_user SET status=?,version=version+1,auth_version=auth_version+?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?", request.status(), revoke, id, version);
        log.info("[用户管理] 状态更新待提交 userId={} oldStatus={} newStatus={}", id, current.status(), request.status());
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return users.view(id);
    }

    /** 管理员重置密码与认证版本同事务，停用状态保持。 */
    @Transactional
    public void resetPassword(long id, ResetPasswordRequest request) {
        long version = Long.parseLong(request.version());
        UserAccount current = users.lockById(id);
        if (current.version() != version) throw new ApiException(ErrorCode.CONFLICT);
        users.updatePassword(id, encoder.encode(request.newPassword()));
        log.info("[用户管理] 密码重置待提交 userId={} version={}", id, version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
    }

    /** 一致快照读取授权专用集合，不要求角色查询权限。 */
    @Transactional(readOnly=true)
    public UserRoles roles(long id) {
        UserAccount user = users.findById(id);
        var ids = jdbc.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=? ORDER BY role_id", Long.class, id);
        return new UserRoles(Long.toString(id), Long.toString(user.version()), ids.stream().map(Object::toString).toList());
    }

    /** 先锁用户，再升序锁旧新角色并集，整体替换且不撤销有效 Token。 */
    @Transactional
    public UserRoles assignRoles(long id, UserRolesRequest request) {
        long version = Long.parseLong(request.version());
        var desired = new java.util.TreeSet<Long>();
        for (String roleId : request.roleIds()) desired.add(Long.parseLong(roleId));
        UserAccount user = users.lockById(id);
        if (user.version() != version) throw new ApiException(ErrorCode.CONFLICT);
        var previous = new java.util.TreeSet<>(jdbc.queryForList("SELECT role_id FROM sys_user_role WHERE user_id=? ORDER BY role_id", Long.class, id));
        var locked = new java.util.TreeSet<>(previous); locked.addAll(desired);
        for (long roleId : locked) {
            var found = jdbc.queryForList("SELECT id FROM sys_role WHERE id=? FOR UPDATE", Long.class, roleId);
            if (found.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (previous.equals(desired)) {
            com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
            return new UserRoles(Long.toString(id), Long.toString(version), desired.stream().map(Object::toString).toList());
        }
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", id);
        for (long roleId : desired) jdbc.update("INSERT INTO sys_user_role(user_id,role_id,created_at) VALUES(?,?,UTC_TIMESTAMP(3))", id, roleId);
        if (jdbc.update("UPDATE sys_user SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND version=?", id, version) != 1)
            throw new ApiException(ErrorCode.CONFLICT);
        log.info("[用户管理] 角色授权待提交 userId={} roleCount={} version={}", id, desired.size(), version);
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(id);
        return new UserRoles(Long.toString(id), Long.toString(version + 1), desired.stream().map(Object::toString).toList());
    }
}
