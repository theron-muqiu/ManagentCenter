package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.security.RedisTokenStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 全部 25 个接口组合验证，使用真实 MySQL/Redis，不运行浏览器端到端测试。 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class SystemContractIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** JSON 契约。 */ @Autowired private ObjectMapper mapper;
    /** 数据库核对。 */ @Autowired private JdbcTemplate jdbc;
    /** Redis故障注入和凭据清理。 */ @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private RedisTokenStore tokens;
    /** 精确接口注册表。 */ @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    /** 限定专用数据库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 全部已设计接口注册且无额外用户删除、日志修改和 CSRF 路由。 */
    @Test void registersExactlyTwentyFiveDesignedEndpoints() {
        var endpoints = new TreeSet<String>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.muqiu.managentcenter"))
                for (var method : info.getMethodsCondition().getMethods()) for (String path : info.getPatternValues()) endpoints.add(method + " " + path);
        });
        assertThat(endpoints).containsExactlyInAnyOrder(
            "POST /api/auth/login", "POST /api/auth/logout", "GET /api/auth/me", "PUT /api/auth/password",
            "GET /api/users", "POST /api/users", "GET /api/users/{id}", "PUT /api/users/{id}", "PUT /api/users/{id}/status", "PUT /api/users/{id}/password", "GET /api/users/{id}/roles", "PUT /api/users/{id}/roles",
            "GET /api/roles", "POST /api/roles", "GET /api/roles/{id}", "PUT /api/roles/{id}", "DELETE /api/roles/{id}", "GET /api/roles/{id}/permissions", "PUT /api/roles/{id}/permissions",
            "GET /api/permissions/tree", "GET /api/role-options", "GET /api/operation-logs", "GET /api/operation-logs/{id}", "GET /api/login-logs", "GET /api/login-logs/{id}");
    }
    /** 经真实接口完成账号、角色、动态授权、改密及历史详情闭环。 */
    @Test void allApiContractsAndTenMutationEventsWorkTogether() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "contract_" + suffix;
        String password = UUID.randomUUID().toString();
        String resetPassword = UUID.randomUUID().toString();
        String changedPassword = UUID.randomUUID().toString();
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer")).andReturn();
        String adminToken = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        String header = "Bearer " + adminToken;
        String userToken = null; String finalToken = null;
        Long userId = null; Long roleId = null;
        List<MvcResult> mutations = new ArrayList<>();
        try {
            mvc.perform(get("/api/auth/me").header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.user.superAdmin").value(true));
            var createdUser = mvc.perform(post("/api/users").header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("username", username, "displayName", "接口闭环测试", "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("0")).andReturn(); mutations.add(createdUser);
            userId = Long.parseLong(mapper.readTree(createdUser.getResponse().getContentAsString()).path("data").path("id").asText());
            mvc.perform(get("/api/users").header("Authorization", header).param("keyword", username)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/users/" + userId).header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(username));
            mutations.add(mvc.perform(put("/api/users/" + userId).header("Authorization", header).contentType("application/json").content("{\"version\":\"0\",\"displayName\":\"编辑后名称\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("1")).andReturn());
            mutations.add(mvc.perform(put("/api/users/" + userId + "/status").header("Authorization", header).contentType("application/json").content("{\"version\":\"1\",\"status\":0}"))
                .andExpect(status().isOk()).andReturn());
            mutations.add(mvc.perform(put("/api/users/" + userId + "/status").header("Authorization", header).contentType("application/json").content("{\"version\":\"2\",\"status\":1}"))
                .andExpect(status().isOk()).andReturn());
            mutations.add(mvc.perform(put("/api/users/" + userId + "/password").header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("version", "3", "newPassword", resetPassword))))
                .andExpect(status().isOk()).andReturn());
            var userLogin = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", username, "password", resetPassword))))
                .andExpect(status().isOk()).andReturn();
            userToken = mapper.readTree(userLogin.getResponse().getContentAsString()).path("data").path("token").asText();
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionCodes.length()").value(0));
            mutations.add(mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + userToken).contentType("application/json").content(mapper.writeValueAsString(Map.of("oldPassword", resetPassword, "newPassword", changedPassword))))
                .andExpect(status().isOk()).andReturn());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + userToken)).andExpect(status().isUnauthorized());
            var nextLogin = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", username, "password", changedPassword))))
                .andExpect(status().isOk()).andReturn();
            finalToken = mapper.readTree(nextLogin.getResponse().getContentAsString()).path("data").path("token").asText();
            var createdRole = mvc.perform(post("/api/roles").header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("name", "contract_role_" + suffix))))
                .andExpect(status().isOk()).andReturn(); mutations.add(createdRole);
            roleId = Long.parseLong(mapper.readTree(createdRole.getResponse().getContentAsString()).path("data").path("id").asText());
            mvc.perform(get("/api/roles").header("Authorization", header).param("keyword", "contract_role_" + suffix)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/roles/" + roleId).header("Authorization", header)).andExpect(status().isOk());
            mutations.add(mvc.perform(put("/api/roles/" + roleId).header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("version", "0", "name", "contract_role_" + suffix, "description", "组合权限"))))
                .andExpect(status().isOk()).andReturn());
            mvc.perform(get("/api/permissions/tree").header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
            String userQuery = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='user:query'", Long.class).toString();
            mutations.add(mvc.perform(put("/api/roles/" + roleId + "/permissions").header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("version", "1", "permissionIds", List.of(userQuery)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("2")).andReturn());
            mvc.perform(get("/api/roles/" + roleId + "/permissions").header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionIds.length()").value(1));
            mvc.perform(get("/api/role-options").header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
            mvc.perform(get("/api/users/" + userId + "/roles").header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("5"));
            mutations.add(mvc.perform(put("/api/users/" + userId + "/roles").header("Authorization", header).contentType("application/json").content(mapper.writeValueAsString(Map.of("version", "5", "roleIds", List.of(roleId.toString())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value("6")).andReturn());
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + finalToken)).andExpect(status().isOk());
            mvc.perform(post("/api/users").header("Authorization", "Bearer " + finalToken).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
            mutations.add(mvc.perform(put("/api/users/" + userId + "/roles").header("Authorization", header).contentType("application/json").content("{\"version\":\"6\",\"roleIds\":[]}"))
                .andExpect(status().isOk()).andReturn());
            var deletedRole = mvc.perform(delete("/api/roles/" + roleId).header("Authorization", header).param("version", "2"))
                .andExpect(status().isOk()).andReturn(); mutations.add(deletedRole);
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("operationType", "3")).andExpect(status().isOk());
            long deletedLogId = jdbc.queryForObject("SELECT id FROM sys_operation_log WHERE request_id=?", Long.class, deletedRole.getResponse().getHeader("X-Request-Id"));
            mvc.perform(get("/api/operation-logs/" + deletedLogId).header("Authorization", header)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetId").value(roleId.toString())).andExpect(jsonPath("$.data.result").value(1));
            mvc.perform(get("/api/login-logs").header("Authorization", header).param("attemptedUsername", username)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
            long loginId = jdbc.queryForObject("SELECT id FROM sys_login_log WHERE request_id=?", Long.class, userLogin.getResponse().getHeader("X-Request-Id"));
            mvc.perform(get("/api/login-logs/" + loginId).header("Authorization", header)).andExpect(status().isOk()).andExpect(jsonPath("$.data.attemptedUsername").value(username));
            var seen = new TreeSet<String>();
            for (MvcResult mutation : mutations) {
                String requestId = mutation.getResponse().getHeader("X-Request-Id");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=1 AND error_code=0", Integer.class, requestId)).isEqualTo(1);
                var event = jdbc.queryForMap("SELECT request_method,request_path FROM sys_operation_log WHERE request_id=?", requestId);
                seen.add(event.get("request_method") + " " + event.get("request_path"));
            }
            assertThat(seen).hasSize(10);
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + finalToken)).andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + finalToken)).andExpect(status().isUnauthorized());
        } finally {
            tokens.revoke(adminToken); if (userToken != null) tokens.revoke(userToken); if (finalToken != null) tokens.revoke(finalToken);
            if (userId != null) { jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId); }
            if (roleId != null) { jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", roleId); jdbc.update("DELETE FROM sys_role WHERE id=?", roleId); }
        }
    }
    /** 十个写入口早期拒绝各记录一次，缺失身份不伪造操作人。 */
    @Test void allTenUnauthenticatedMutationsAreRecorded() throws Exception {
        String[] paths = {"/api/users", "/api/users/1", "/api/users/1/status", "/api/users/1/password", "/api/users/1/roles", "/api/roles", "/api/roles/1", "/api/roles/1", "/api/roles/1/permissions", "/api/auth/password"};
        String[] methods = {"POST", "PUT", "PUT", "PUT", "PUT", "POST", "PUT", "DELETE", "PUT", "PUT"};
        for (int index = 0; index < paths.length; index++) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(methods[index]), paths[index]);
            var failed = mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andReturn();
            String requestId = failed.getResponse().getHeader("X-Request-Id");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=0 AND error_code=40101 AND operator_id IS NULL", Integer.class, requestId)).isEqualTo(1);
        }
    }
    /** 授权拒绝、版本冲突和非法参数记录原错误，操作人取已验证身份。 */
    @Test void forbiddenConflictAndValidationKeepAccurateFailureHistory() throws Exception {
        String username = "contract_denied_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,'失败历史测试','fixture',1,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username);
        long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var user = tokens.issue(userId, 0);
        var admin = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        try {
            var forbidden = mvc.perform(post("/api/roles").header("Authorization", "Bearer " + user.token()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=0 AND error_code=40301 AND operator_id=?", Integer.class,
                forbidden.getResponse().getHeader("X-Request-Id"), userId)).isEqualTo(1);
            var conflict = mvc.perform(put("/api/users/" + userId).header("Authorization", "Bearer " + admin.token()).contentType("application/json")
                .content("{\"version\":\"1\",\"displayName\":\"不能覆盖\"}")).andExpect(status().isConflict()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=0 AND error_code=40902", Integer.class,
                conflict.getResponse().getHeader("X-Request-Id"))).isEqualTo(1);
            var invalid = mvc.perform(put("/api/users/" + userId).header("Authorization", "Bearer " + admin.token()).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=? AND result=0 AND error_code=40001", Integer.class,
                invalid.getResponse().getHeader("X-Request-Id"))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM sys_user WHERE id=?", Long.class, userId)).isZero();
        } finally { tokens.revoke(user.token()); tokens.revoke(admin.token()); jdbc.update("DELETE FROM sys_user WHERE id=?", userId); }
    }

    /** Redis读取及签发故障拒绝认证，并保留相应安全失败历史。 */
    @Test void redisFaultsNeverAuthorizeAndQueryCredentialsAreRejected() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        long version = jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId);
        var token = tokens.issue(adminId, version);
        try {
            org.mockito.Mockito.doThrow(new ApiException(ErrorCode.REDIS_UNAVAILABLE)).when(tokens).find(token.token());
            var failed = mvc.perform(put("/api/users/1").header("Authorization", "Bearer " + token.token()).contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(50301)).andReturn();
            assertThat(jdbc.queryForObject("SELECT error_code FROM sys_operation_log WHERE request_id=?", Integer.class, failed.getResponse().getHeader("X-Request-Id"))).isEqualTo(50301);
            org.mockito.Mockito.doCallRealMethod().when(tokens).find(token.token());
            org.mockito.Mockito.doThrow(new ApiException(ErrorCode.REDIS_UNAVAILABLE)).when(tokens).issue(adminId, version);
            var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")))))
                .andExpect(status().isServiceUnavailable()).andReturn();
            assertThat(jdbc.queryForObject("SELECT error_code FROM sys_login_log WHERE request_id=?", Integer.class, login.getResponse().getHeader("X-Request-Id"))).isEqualTo(50301);
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token.token()).param("token", "fixture"))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/auth/login").param("access_token", "fixture").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
            mvc.perform(get("/api/auth/me").cookie(new jakarta.servlet.http.Cookie("token", token.token())))
                .andExpect(status().isUnauthorized());
        } finally { tokens.revoke(token.token()); }
    }
}
