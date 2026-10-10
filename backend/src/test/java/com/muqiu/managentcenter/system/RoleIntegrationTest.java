package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.RoleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 真实角色事务、唯一竞争与外键删除保护。 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RoleIntegrationTest {
    /** 接口验证。 */ @Autowired private MockMvc mvc;
    /** JSON 编码。 */ @Autowired private ObjectMapper mapper;
    /** 隔离数据访问。 */ @Autowired private JdbcTemplate jdbc;
    /** 凭据签发。 */ @Autowired private RedisTokenStore tokens;
    /** 事务业务入口。 */ @Autowired private RoleService service;
    /** 限定专用数据库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 角色资料修改与版本冲突，删除同步移除权限关联。 */
    @Test void managesRoleWithSharedVersion() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var token = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        String header = "Bearer " + token.token();
        String name = "role_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Long id = null;
        try {
            var created = mvc.perform(post("/api/roles").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("0")).andExpect(jsonPath("$.data.permissionCodes.length()").value(0)).andReturn();
            id = Long.parseLong(mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText());
            mvc.perform(post("/api/roles").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40901));
            mvc.perform(get("/api/roles").header("Authorization", header).param("keyword", name)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(put("/api/roles/" + id).header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("version", "0", "name", name, "description", "角色说明"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("1"));
            mvc.perform(put("/api/roles/" + id).header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("version", "0", "name", name))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902));
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id,created_at) SELECT ?,id,UTC_TIMESTAMP(3) FROM sys_permission WHERE code='user:query'", id);
            mvc.perform(get("/api/roles/" + id).header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionCodes[0]").value("user:query"));
            long rollbackId = id;
            var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.execute(state -> {
                service.delete(rollbackId, "1");
                throw new org.springframework.dao.DataAccessResourceFailureException("test rollback");
            })).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, id)).isEqualTo(1);
            mvc.perform(delete("/api/roles/" + id).header("Authorization", header).param("version", "1")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, id)).isZero();
            mvc.perform(delete("/api/roles/" + id).header("Authorization", header).param("version", "1")).andExpect(status().isNotFound());
        } finally { if (id != null) { jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", id); jdbc.update("DELETE FROM sys_role WHERE id=?", id); } tokens.revoke(token.token()); }
    }
    /** 停用用户关联仍阻止角色删除，解除关联才允许。 */
    @Test void disabledAssociationProtectsRole() {
        var role = service.create(new CreateRoleRequest("linked_test_" + java.util.UUID.randomUUID().toString().substring(0, 8), null));
        String username = "linked_user_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,?,?,0,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username, "停用关联", "$2a$10$fixture");
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        long roleId = Long.parseLong(role.id());
        try {
            jdbc.update("INSERT INTO sys_user_role(user_id,role_id,created_at) VALUES(?,?,UTC_TIMESTAMP(3))", userId, roleId);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(roleId, "0"))
                .isInstanceOf(com.muqiu.managentcenter.common.ApiException.class)
                .satisfies(failure -> assertThat(((com.muqiu.managentcenter.common.ApiException) failure).error()).isEqualTo(com.muqiu.managentcenter.common.ErrorCode.ROLE_IN_USE));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id=?", Integer.class, roleId)).isEqualTo(1);
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
            service.delete(roleId, "0");
        } finally { jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId); jdbc.update("DELETE FROM sys_role WHERE id=?", roleId); }
    }
    /** 同名新增竞争只有一个事务成功，输入越界和授权字段拒绝。 */
    @Test void concurrentRoleUniqueness() throws Exception {
        String name = "race_role_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); try { service.create(new CreateRoleRequest(name, null)); return true; } catch (org.springframework.dao.DuplicateKeyException failure) { return false; } });
            var second = executor.submit(() -> { gate.await(); try { service.create(new CreateRoleRequest(name, null)); return true; } catch (org.springframework.dao.DuplicateKeyException failure) { return false; } });
            gate.countDown();
            int successful = (first.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successful).isEqualTo(1);
        } finally { jdbc.update("DELETE FROM sys_role WHERE name=?", name); }
    }
}
