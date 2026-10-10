package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.dto.RolePermissionsRequest;
import com.muqiu.managentcenter.system.dto.UserRolesRequest;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.RoleService;
import com.muqiu.managentcenter.system.service.UserService;
import com.muqiu.managentcenter.system.vo.RoleView;
import com.muqiu.managentcenter.system.vo.UserView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 多角色并集、即时授权、整体替换及删除竞争的真实依赖验证。 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class AuthorizationIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** 用户事务。 */ @Autowired private UserService users;
    /** 角色事务。 */ @Autowired private RoleService roles;
    /** 数据库隔离清理。 */ @Autowired private JdbcTemplate jdbc;
    /** 当前请求凭据。 */ @Autowired private RedisTokenStore tokens;
    /** 限定专用测试库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 同一 Token 在授予、部分解除和全部撤销后逐请求使用最新权限。 */
    @Test void unionAndImmediateChanges() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        UserView user = users.create(new CreateUserRequest("union_" + suffix, "并集测试", "fixture", null, null));
        RoleView a = roles.create(new CreateRoleRequest("union_a_" + suffix, null));
        RoleView b = roles.create(new CreateRoleRequest("union_b_" + suffix, null));
        long userId = Long.parseLong(user.id()); long aId = Long.parseLong(a.id()); long bId = Long.parseLong(b.id());
        String userQuery = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='user:query'", Long.class).toString();
        String roleQuery = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='role:query'", Long.class).toString();
        var token = tokens.issue(userId, 0);
        try {
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            roles.assignPermissions(aId, new RolePermissionsRequest("0", List.of(userQuery, userQuery)));
            roles.assignPermissions(bId, new RolePermissionsRequest("0", List.of(userQuery, roleQuery)));
            var assigned = users.assignRoles(userId, new UserRolesRequest("0", List.of(a.id(), b.id(), a.id())));
            assertThat(assigned.roleIds()).hasSize(2);
            assertThat(users.assignRoles(userId, new UserRolesRequest(assigned.version(), assigned.roleIds())).version()).isEqualTo(assigned.version());
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/roles").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(post("/api/users").header("Authorization", "Bearer " + token.token()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCodes.length()").value(2));
            var remaining = users.assignRoles(userId, new UserRolesRequest(assigned.version(), List.of(b.id())));
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            roles.assignPermissions(bId, new RolePermissionsRequest("1", List.of()));
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            users.assignRoles(userId, new UserRolesRequest(remaining.version(), List.of()));
            assertThat(jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, userId)).isZero();
        } finally {
            tokens.revoke(token.token()); jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
            jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN (?,?)", aId, bId); jdbc.update("DELETE FROM sys_role WHERE id IN (?,?)", aId, bId);
        }
    }
    /** 无效集合和过期版本不会部分保存，授权接口不附加普通查询权限。 */
    @Test void rejectsPartialGrantsAndKeepsIndependentPermissions() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        var user = users.create(new CreateUserRequest("grant_" + suffix, "授权测试", "fixture", null, null));
        var role = roles.create(new CreateRoleRequest("grant_role_" + suffix, null));
        long userId = Long.parseLong(user.id()); long roleId = Long.parseLong(role.id());
        var token = tokens.issue(userId, 0);
        String assignRole = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='user:assign-role'", Long.class).toString();
        String assignPermission = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='role:assign-permission'", Long.class).toString();
        try {
            roles.assignPermissions(roleId, new RolePermissionsRequest("0", List.of(assignRole, assignPermission)));
            users.assignRoles(userId, new UserRolesRequest("0", List.of(role.id())));
            mvc.perform(get("/api/users/" + userId + "/roles").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/role-options").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/roles/" + roleId + "/permissions").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/permissions/tree").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            mvc.perform(get("/api/roles").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> users.assignRoles(userId, new UserRolesRequest("1", List.of(role.id(), "9223372036854775807"))))
                .isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).error()).isEqualTo(ErrorCode.NOT_FOUND));
            assertThat(users.roles(userId).roleIds()).containsExactly(role.id());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> roles.assignPermissions(roleId, new RolePermissionsRequest("1", List.of(assignRole, "9223372036854775807"))))
                .isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).error()).isEqualTo(ErrorCode.NOT_FOUND));
            assertThat(roles.permissions(roleId).permissionIds()).hasSize(2);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> roles.assignPermissions(roleId, new RolePermissionsRequest("0", List.of())))
                .isInstanceOf(ApiException.class).satisfies(error -> assertThat(((ApiException) error).error()).isEqualTo(ErrorCode.CONFLICT));
        } finally {
            tokens.revoke(token.token()); jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
            jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", roleId); jdbc.update("DELETE FROM sys_role WHERE id=?", roleId);
        }
    }
    /** 删除与分配竞争，成功关联始终引用存在角色。 */
    @Test void concurrentDeletionAndAssignmentNeverOrphans() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        var user = users.create(new CreateUserRequest("delete_race_" + suffix, "删除竞争", "fixture", null, null));
        var role = roles.create(new CreateRoleRequest("delete_race_" + suffix, null));
        long userId = Long.parseLong(user.id()); long roleId = Long.parseLong(role.id());
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var assign = executor.submit(() -> {
                gate.await();
                try { users.assignRoles(userId, new UserRolesRequest("0", List.of(role.id()))); return true; }
                catch (ApiException failure) { assertThat(failure.error()).isIn(ErrorCode.NOT_FOUND, ErrorCode.CONFLICT); return false; }
                catch (org.springframework.dao.CannotAcquireLockException failure) { return false; }
            });
            var delete = executor.submit(() -> {
                gate.await();
                try { roles.delete(roleId, "0"); return true; }
                catch (ApiException failure) { assertThat(failure.error()).isIn(ErrorCode.ROLE_IN_USE, ErrorCode.CONFLICT); return false; }
                catch (org.springframework.dao.CannotAcquireLockException failure) { return false; }
            });
            gate.countDown();
            boolean assigned = assign.get(20, java.util.concurrent.TimeUnit.SECONDS); boolean deleted = delete.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(assigned && deleted).isFalse();
            assertThat(assigned || deleted).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur LEFT JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.id IS NULL", Integer.class, userId)).isZero();
        } finally { jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId); jdbc.update("DELETE FROM sys_role WHERE id=?", roleId); }
    }
}
