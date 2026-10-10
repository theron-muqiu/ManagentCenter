package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.repository.UserRepository;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.UserService;

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

/** 用户 API 在真实事务与认证链下的状态和边界验证。 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class UserIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** JSON 编码。 */ @Autowired private ObjectMapper mapper;
    /** 数据查询与隔离清理。 */ @Autowired private JdbcTemplate jdbc;
    /** 专用凭据。 */ @Autowired private RedisTokenStore tokens;
    /** 并发事务入口。 */ @Autowired private UserService service;
    /** 故障注入点。 */ @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private UserRepository users;
    /** 限定专用测试库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 创建、资料替换、启停和重置符合版本及全部凭据撤销规则。 */
    @Test void managesUserAndRevokesOldCredentials() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        long adminVersion = jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId);
        var admin = tokens.issue(adminId, adminVersion);
        String username = "user_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = java.util.UUID.randomUUID().toString();
        String header = "Bearer " + admin.token();
        Long id = null;
        try {
            var created = mvc.perform(post("/api/users").header("Authorization", header).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", username, "displayName", "用户测试", "password", password, "phone", "  +12345678  ", "email", "   "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(1)).andExpect(jsonPath("$.data.phone").value("+12345678"))
                .andExpect(jsonPath("$.data.email").isEmpty()).andExpect(jsonPath("$.data.roles.length()").value(0)).andReturn();
            id = Long.parseLong(mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText());
            var old = tokens.issue(id, 0);
            try {
                mvc.perform(get("/api/users/" + id).header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("0"));
                mvc.perform(get("/api/users").header("Authorization", header).param("keyword", username)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
                mvc.perform(put("/api/users/" + id).header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\",\"displayName\":\"编辑测试\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username)).andExpect(jsonPath("$.data.version").value("1")).andExpect(jsonPath("$.data.phone").isEmpty());
                mvc.perform(put("/api/users/" + id).header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\",\"displayName\":\"过期更新\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40902));
                mvc.perform(put("/api/users/" + id + "/status").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1\",\"status\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("2"));
                mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + old.token())).andExpect(status().isUnauthorized());
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40302));
                mvc.perform(put("/api/users/" + id + "/status").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"2\",\"status\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("2"));
                mvc.perform(put("/api/users/" + id + "/status").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"2\",\"status\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("3"));
                mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + old.token())).andExpect(status().isUnauthorized());
                var current = tokens.issue(id, 1);
                String replacement = java.util.UUID.randomUUID().toString();
                try {
                    mvc.perform(put("/api/users/" + id + "/password").header("Authorization", header).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("version", "3", "newPassword", replacement)))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
                    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + current.token())).andExpect(status().isUnauthorized());
                    assertThat(users.findById(id).authVersion()).isEqualTo(2);
                } finally { tokens.revoke(current.token()); }
            } finally { tokens.revoke(old.token()); }
            mvc.perform(put("/api/users/" + adminId + "/status").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\",\"status\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(40904));
        } finally { if (id != null) jdbc.update("DELETE FROM sys_user WHERE id=?", id); tokens.revoke(admin.token()); }
    }
    /** 白名单、格式、缺失对象、字面通配符和无权限请求不能保存无效数据。 */
    @Test void rejectsInvalidInputAndUnauthorizedMutation() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var admin = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        try {
            String header = "Bearer " + admin.token();
            mvc.perform(get("/api/users/0").header("Authorization", header)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/users/9223372036854775807").header("Authorization", header)).andExpect(status().isNotFound());
            mvc.perform(get("/api/users").header("Authorization", header).param("page", "0")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/users").header("Authorization", header).param("keyword", "%no_such_literal%")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(put("/api/users/" + adminId).header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"0\",\"displayName\":\"不保存\",\"username\":\"changed\"}"))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/users").header("Authorization", header).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"invalid\",\"displayName\":\"测试\",\"password\":\"fixture\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        } finally { tokens.revoke(admin.token()); }
    }
    /** 重置过程写入后抛异常，密码、资料版本、认证版本整体回滚。 */
    @Test void resetFailureRollsBackAllVersions() throws Exception {
        String username = "rollback_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var user = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()))
            .execute(status -> { jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,?,?,1,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username, "回滚测试", "$2a$10$fixture"); return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username); });
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var admin = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        var old = tokens.issue(user, 0);
        try {
            org.mockito.Mockito.doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataAccessResourceFailureException("test fault"); })
                .when(users).updatePassword(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.anyString());
            var failed = mvc.perform(put("/api/users/" + user + "/password").header("Authorization", "Bearer " + admin.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"0\",\"newPassword\":\"replacement-fixture\"}")).andExpect(status().isInternalServerError()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=0 AND error_code=50000", Integer.class,
                failed.getResponse().getHeader("X-Request-Id"))).isEqualTo(1);
            assertThat(users.findById(user).version()).isZero();
            assertThat(users.findById(user).authVersion()).isZero();
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + old.token())).andExpect(status().isOk());
        } finally { tokens.revoke(old.token()); tokens.revoke(admin.token()); jdbc.update("DELETE FROM sys_user WHERE id=?", user); }
    }
    /** 同名竞争只成功一次，大小写不同和重复联系方式允许共存。 */
    @Test void concurrentDuplicateAndCaseSensitiveContacts() throws Exception {
        String username = "case_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var request = new CreateUserRequest(username, "并发创建测试", "fixture", "+12345678", "same@example.com");
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); try { service.create(request); return true; } catch (org.springframework.dao.DuplicateKeyException conflict) { return false; } });
            var second = executor.submit(() -> { gate.await(); try { service.create(request); return true; } catch (org.springframework.dao.DuplicateKeyException conflict) { return false; } });
            gate.countDown();
            int successful = (first.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successful).isEqualTo(1);
            var upper = service.create(new CreateUserRequest(username.toUpperCase(java.util.Locale.ROOT), "大小写测试", "fixture", "+12345678", "same@example.com"));
            assertThat(upper.username()).isEqualTo(username.toUpperCase(java.util.Locale.ROOT));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username IN (?,?)", Integer.class, username, username.toUpperCase(java.util.Locale.ROOT))).isEqualTo(2);
        } finally { jdbc.update("DELETE FROM sys_user WHERE username IN (?,?)", username, username.toUpperCase(java.util.Locale.ROOT)); }
    }
}
