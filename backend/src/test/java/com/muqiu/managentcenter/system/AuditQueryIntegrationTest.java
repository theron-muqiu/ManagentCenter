package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.dto.RolePermissionsRequest;
import com.muqiu.managentcenter.system.dto.UserRolesRequest;
import com.muqiu.managentcenter.system.repository.OperationLogRepository;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.RoleService;
import com.muqiu.managentcenter.system.service.UserService;

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

/** 两类日志的精确筛选、时间边界、历史独立性及查看权限验证。 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class AuditQueryIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** 隔离历史数据。 */ @Autowired private JdbcTemplate jdbc;
    /** 认证凭据。 */ @Autowired private RedisTokenStore tokens;
    /** 授权对象创建。 */ @Autowired private UserService users;
    /** 授权及删除。 */ @Autowired private RoleService roles;
    /** 查询故障注入。 */ @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private OperationLogRepository operationLogs;
    /** 限定专用测试库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 分页固定排序、大小写精确匹配和半开时间范围符合两个列表契约。 */
    @Test void filtersAndDetailsUseHistoricalSnapshots() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var token = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        String account = "AuditCase_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String loginRequest = java.util.UUID.randomUUID().toString();
        long operationId = 0;
        try {
            for (int index = 0; index < 3; index++) {
                String requestId = java.util.UUID.randomUUID().toString();
                jdbc.update("INSERT INTO sys_operation_log(operator_id,operator_username,operator_display_name,module,feature,operation_type,target_type,target_id,request_method,request_path,summary,request_id,occurred_at,result,error_code,client_ip,user_agent,duration_ms,created_at) VALUES(999999,?,'历史显示名','user','历史测试',?,0,999998,'PUT','/api/users/{id}','{\"changedFields\":[\"displayName\"]}',?,'2000-01-01 00:00:00.000',1,0,'127.0.0.1','test-agent',1,'2000-01-01 00:00:00.001')", account, index, requestId);
                operationId = jdbc.queryForObject("SELECT id FROM sys_operation_log WHERE request_id=?", Long.class, requestId);
            }
            jdbc.update("INSERT INTO sys_login_log(user_id,attempted_username,request_id,occurred_at,result,error_code,failure_reason,client_ip,user_agent,duration_ms,created_at) VALUES(999999,?,?,'2000-01-01 00:00:00.000',0,40102,'账号或密码错误','127.0.0.1','test-agent',2,'2000-01-01 00:00:00.002')", account, loginRequest);
            long loginId = jdbc.queryForObject("SELECT id FROM sys_login_log WHERE request_id=?", Long.class, loginRequest);
            String header = "Bearer " + token.token();
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("operatorUsername", account).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items[0].id").value(Long.toString(operationId)))
                .andExpect(jsonPath("$.data.items[0].summary").doesNotExist());
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("operatorUsername", account.toLowerCase(java.util.Locale.ROOT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("operatorUsername", account).param("operationType", "2")
                .param("startTime", "2000-01-01T00:00:00Z").param("endTime", "2000-01-01T00:00:00.001Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("operatorUsername", account).param("endTime", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
            var detail = mvc.perform(get("/api/operation-logs/" + operationId).header("Authorization", header)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operatorId").value("999999")).andExpect(jsonPath("$.data.targetId").value("999998"))
                .andExpect(jsonPath("$.data.operatorDisplayName").value("历史显示名")).andExpect(jsonPath("$.data.summary.changedFields[0]").value("displayName")).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=?", Integer.class, detail.getResponse().getHeader("X-Request-Id"))).isZero();
            mvc.perform(get("/api/login-logs").header("Authorization", header).param("attemptedUsername", account).param("result", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].userAgent").doesNotExist());
            mvc.perform(get("/api/login-logs/" + loginId).header("Authorization", header)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userAgent").value("test-agent")).andExpect(jsonPath("$.data.userId").value("999999"));
        } finally { tokens.revoke(token.token()); jdbc.update("DELETE FROM sys_operation_log WHERE operator_username=?", account); jdbc.update("DELETE FROM sys_login_log WHERE request_id=?", loginRequest); }
    }
    /** 操作日志权限与登录日志权限独立；用户查询权不隐式授予日志查看。 */
    @Test void logPermissionsAreIndependentAndHistoricalRoleDeletionIsAllowed() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        var user = users.create(new CreateUserRequest("log_reader_" + suffix, "日志查看者", "fixture", null, null));
        var role = roles.create(new CreateRoleRequest("log_reader_role_" + suffix, null));
        long userId = Long.parseLong(user.id()); long roleId = Long.parseLong(role.id());
        String operation = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='operation-log:query'", Long.class).toString();
        String login = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='login-log:query'", Long.class).toString();
        String query = jdbc.queryForObject("SELECT id FROM sys_permission WHERE code='user:query'", Long.class).toString();
        var token = tokens.issue(userId, 0);
        try {
            roles.assignPermissions(roleId, new RolePermissionsRequest("0", List.of(operation)));
            users.assignRoles(userId, new UserRolesRequest("0", List.of(role.id())));
            mvc.perform(get("/api/operation-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/login-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            roles.assignPermissions(roleId, new RolePermissionsRequest("1", List.of(login)));
            mvc.perform(get("/api/login-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isOk());
            mvc.perform(get("/api/operation-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            roles.assignPermissions(roleId, new RolePermissionsRequest("2", List.of(query)));
            mvc.perform(get("/api/login-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            mvc.perform(get("/api/operation-logs").header("Authorization", "Bearer " + token.token())).andExpect(status().isForbidden());
            users.assignRoles(userId, new UserRolesRequest("1", List.of()));
            roles.delete(roleId, "3");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id=?", Integer.class, roleId)).isZero();
        } finally { tokens.revoke(token.token()); jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", userId); jdbc.update("DELETE FROM sys_user WHERE id=?", userId); jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", roleId); jdbc.update("DELETE FROM sys_role WHERE id=?", roleId); }
    }
    /** 非法范围、记录缺失和查询存储异常分别返回明确失败。 */
    @Test void rejectsInvalidRangesAndDoesNotHideReadFailures() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var token = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        try {
            String header = "Bearer " + token.token();
            mvc.perform(get("/api/operation-logs").header("Authorization", header).param("startTime", "2000-01-02T00:00:00Z").param("endTime", "2000-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
            mvc.perform(get("/api/login-logs").header("Authorization", header).param("startTime", "invalid"))
                .andExpect(status().isBadRequest());
            mvc.perform(get("/api/login-logs").header("Authorization", header).param("startTime", "2000-01-01T00:00:00+08:00"))
                .andExpect(status().isBadRequest());
            mvc.perform(get("/api/login-logs").header("Authorization", header).param("result", "2")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/operation-logs/9223372036854775807").header("Authorization", header)).andExpect(status().isNotFound());
            mvc.perform(get("/api/login-logs/9223372036854775807").header("Authorization", header)).andExpect(status().isNotFound());
            org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("test fault")).when(operationLogs).detail(123456L);
            mvc.perform(get("/api/operation-logs/123456").header("Authorization", header)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000));
            mvc.perform(delete("/api/operation-logs/1").header("Authorization", header)).andExpect(status().isForbidden());
        } finally { tokens.revoke(token.token()); }
    }
}
