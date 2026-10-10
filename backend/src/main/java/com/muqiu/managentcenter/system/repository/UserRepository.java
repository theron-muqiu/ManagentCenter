package com.muqiu.managentcenter.system.repository;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.entity.UserAccount;
import com.muqiu.managentcenter.system.vo.RoleOption;
import com.muqiu.managentcenter.system.vo.UserView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 参数化用户数据访问，密码摘要只用于内部认证。 */
@Repository
public class UserRepository {
    /** 明确内部字段映射，不向接口直接序列化。 */
    private static final RowMapper<UserAccount> ACCOUNT = UserRepository::mapAccount;
    /** 数据库组件。 */
    private final JdbcTemplate jdbc;

    /** 注入数据库组件。 */
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 从 UTC 数据库列构造内部账号。 */
    private static UserAccount mapAccount(ResultSet row, int index) throws SQLException {
        return new UserAccount(row.getLong("id"), row.getString("username"), row.getString("display_name"),
                row.getString("password_hash"), row.getInt("status"), row.getBoolean("is_super_admin"),
                row.getString("phone"), row.getString("email"), row.getLong("version"), row.getLong("auth_version"),
                row.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                row.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    /** 精确大小写敏感账号检索，不转换账号。 */
    public UserAccount findByUsername(String username) {
        List<UserAccount> users = jdbc.query("SELECT * FROM sys_user WHERE username=?", ACCOUNT, username);
        return users.isEmpty() ? null : users.getFirst();
    }

    /** 返回存在的用户，不存在则明确失败。 */
    public UserAccount findById(long id) {
        List<UserAccount> users = jdbc.query("SELECT * FROM sys_user WHERE id=?", ACCOUNT, id);
        if (users.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        return users.getFirst();
    }

    /** 用户行锁供登录、改密、启停及授权串行化使用。 */
    public UserAccount lockById(long id) {
        List<UserAccount> users = jdbc.query("SELECT * FROM sys_user WHERE id=? FOR UPDATE", ACCOUNT, id);
        if (users.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        return users.getFirst();
    }

    /** 返回仅含公开字段及角色摘要的 DTO。 */
    public UserView view(long id) {
        UserAccount user = findById(id);
        List<RoleOption> roles = jdbc.query("SELECT r.id,r.name FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.id",
                (row, index) -> new RoleOption(Long.toString(row.getLong("id")), row.getString("name")), id);
        return new UserView(Long.toString(user.id()), user.username(), user.displayName(), user.phone(), user.email(),
                user.status(), user.superAdmin(), Long.toString(user.version()), user.createdAt(), user.updatedAt(), roles);
    }

    /** 调用方持有用户行锁，原子修改密码及资料和认证版本。 */
    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE sys_user SET password_hash=?,version=version+1,auth_version=auth_version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?", passwordHash, id);
    }
}
