package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.audit.AuditCaptureFilter;
import com.muqiu.managentcenter.system.audit.RequestAuditContext;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.AuditRecordWriter;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 持久化日志真实提交、早期拒绝、脱敏及失败隔离验证。 */
@SpringBootTest(properties="audit.trusted-proxies=10.0.0.1")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@org.springframework.context.annotation.Import(AuditCaptureIntegrationTest.ResponseFailure.class)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AuditCaptureIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** JSON 编码。 */ @Autowired private ObjectMapper mapper;
    /** 历史读取。 */ @Autowired private JdbcTemplate jdbc;
    /** 凭据清理。 */ @Autowired private RedisTokenStore tokens;
    /** 独立写入故障点。 */ @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private AuditRecordWriter writer;
    /** 专用库独立锁连接。 */ @Autowired private javax.sql.DataSource source;
    /** 限定专用库。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test")) throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 每个登录请求一条历史，包括非法 JSON；不重复产生操作记录。 */
    @Test void loginResultsAreRecordedOnce() throws Exception {
        var wrong = mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"audit_unknown\",\"password\":\"fixture\"}"))
            .andExpect(status().isUnauthorized()).andReturn();
        String wrongId = wrong.getResponse().getHeader("X-Request-Id");
        var row = jdbc.queryForMap("SELECT attempted_username,result,error_code,user_id FROM sys_login_log WHERE request_id=?", wrongId);
        assertThat(row.get("attempted_username")).isEqualTo("audit_unknown"); assertThat(((Number) row.get("result")).intValue()).isZero();
        assertThat(((Number) row.get("error_code")).intValue()).isEqualTo(40102); assertThat(row.get("user_id")).isNull();
        var invalid = mvc.perform(post("/api/auth/login").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andReturn();
        assertThat(jdbc.queryForObject("SELECT attempted_username FROM sys_login_log WHERE request_id=?", String.class, invalid.getResponse().getHeader("X-Request-Id"))).isNull();
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")))))
            .andExpect(status().isOk()).andReturn();
        String loginId = login.getResponse().getHeader("X-Request-Id");
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        try {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE request_id=? AND result=1 AND error_code=0", Integer.class, loginId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=?", Integer.class, loginId)).isZero();
            var query = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=?", Integer.class, query.getResponse().getHeader("X-Request-Id"))).isZero();
        } finally { tokens.revoke(token); }
    }
    /** 新增记录来自真实提交，联系资料只保留字段名，未认证请求没有伪造操作人。 */
    @Test void committedMutationIsSafeAndEarlyDenialHasNoActor() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var token = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        String username = "audit_user_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Long id = null;
        try {
            var created = mvc.perform(post("/api/users").header("Authorization", "Bearer " + token.token()).header("User-Agent", "audit-test")
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("username", username, "displayName", "审计测试", "password", "secret-fixture", "phone", "+12345678", "email", "private@example.com"))))
                .andExpect(status().isOk()).andReturn();
            id = Long.parseLong(mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText());
            var row = jdbc.queryForMap("SELECT result,operator_id,target_id,summary,request_path FROM sys_operation_log WHERE request_id=?", created.getResponse().getHeader("X-Request-Id"));
            assertThat(((Number) row.get("result")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("operator_id")).longValue()).isEqualTo(adminId);
            assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(id);
            assertThat(row.get("summary").toString().contains("secret-fixture")).isFalse();
            assertThat(row.get("summary").toString().contains("+12345678")).isFalse();
            assertThat(row.get("summary").toString().contains("private@example.com")).isFalse();
            var denied = mvc.perform(put("/api/users/" + id).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andReturn();
            var failed = jdbc.queryForMap("SELECT operator_id,result,error_code,request_path FROM sys_operation_log WHERE request_id=?", denied.getResponse().getHeader("X-Request-Id"));
            assertThat(failed.get("operator_id")).isNull(); assertThat(((Number) failed.get("result")).intValue()).isZero();
            assertThat(failed.get("request_path")).isEqualTo("/api/users/{id}");
        } finally { if (id != null) jdbc.update("DELETE FROM sys_user WHERE id=?", id); tokens.revoke(token.token()); }
    }
    /** 日志表真实约束失败不影响已提交新增和成功登录的凭据。 */
    @Test void realAuditWriteFailureDoesNotRollbackBusiness() throws Exception {
        org.mockito.Mockito.doAnswer(call -> {
            RequestAuditContext context = call.getArgument(0);
            context.userAgent = "x".repeat(700);
            return call.callRealMethod();
        }).when(writer).write(org.mockito.ArgumentMatchers.any());
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")))))
            .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        String username = "audit_fault_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        Long id = null;
        try {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE request_id=?", Integer.class, login.getResponse().getHeader("X-Request-Id"))).isZero();
            var created = mvc.perform(post("/api/users").header("Authorization", "Bearer " + token).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("username", username, "displayName", "写入故障测试", "password", "fixture"))))
                .andExpect(status().isOk()).andReturn();
            id = Long.parseLong(mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asText());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=?", Integer.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE request_id=?", Integer.class, created.getResponse().getHeader("X-Request-Id"))).isZero();
        } finally { if (id != null) jdbc.update("DELETE FROM sys_user WHERE id=?", id); tokens.revoke(token); }
    }

    /** 已提交后响应失败仍保留成功历史；可信代理及浏览器信息按安全规则处理。 */
    @Test void responseFailureDoesNotRewriteCommitAndProxyHeadersAreBounded() throws Exception {
        long adminId = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var token = tokens.issue(adminId, jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, adminId));
        String username = "audit_response_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try {
            var response = mvc.perform(post("/api/users").with(request -> { request.setRemoteAddr("10.0.0.1"); return request; })
                .header("Authorization", "Bearer " + token.token()).header("X-Test-Response-Failure", "once")
                .header("X-Forwarded-For", "198.51.100.23, 10.0.0.1").header("User-Agent", "agent" + "x".repeat(600))
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("username", username, "displayName", "提交后响应故障", "password", "fixture"))))
                .andExpect(status().isInternalServerError()).andReturn();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Integer.class, username)).isEqualTo(1);
            var row = jdbc.queryForMap("SELECT result,error_code,client_ip,user_agent FROM sys_operation_log WHERE request_id=?", response.getResponse().getHeader("X-Request-Id"));
            assertThat(((Number) row.get("result")).intValue()).isEqualTo(1);
            assertThat(((Number) row.get("error_code")).intValue()).isZero();
            assertThat(row.get("client_ip")).isEqualTo("198.51.100.23");
            assertThat(row.get("user_agent").toString()).hasSize(512).doesNotContain("\n");
            var rejected = mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("203.0.113.25"); return request; })
                .header("X-Forwarded-For", "198.51.100.23").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andReturn();
            assertThat(jdbc.queryForObject("SELECT client_ip FROM sys_login_log WHERE request_id=?", String.class, rejected.getResponse().getHeader("X-Request-Id"))).isEqualTo("203.0.113.25");
            // 控制字符会先被 Security 防火墙拒绝；独立验证采集器自身也安全清理。
            var probeWriter = org.mockito.Mockito.mock(AuditRecordWriter.class);
            var probeRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/login");
            probeRequest.addHeader("User-Agent", "agent\n\t" + "x".repeat(600));
            new AuditCaptureFilter(probeWriter, "").doFilter(probeRequest, new org.springframework.mock.web.MockHttpServletResponse(), (request, reply) -> {});
            var captured = org.mockito.ArgumentCaptor.forClass(RequestAuditContext.class);
            org.mockito.Mockito.verify(probeWriter).write(captured.capture());
            assertThat(captured.getValue().userAgent).hasSize(512).doesNotContain("\n", "\t");
        } finally { jdbc.update("DELETE FROM sys_user WHERE username=?", username); tokens.revoke(token.token()); }
    }

    /** 真实唯一键锁阻塞触发独立写入期限，业务结果保留且产生安全 ERROR 告警。 */
    @Test void realWriteTimeoutKeepsSuccessfulLogin(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        org.mockito.Mockito.doAnswer(call -> {
            RequestAuditContext context = call.getArgument(0);
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement("INSERT INTO sys_login_log(request_id,occurred_at,result,error_code,duration_ms,created_at) VALUES(?,UTC_TIMESTAMP(3),1,0,0,UTC_TIMESTAMP(3))")) {
                    statement.setString(1, context.requestId); statement.executeUpdate();
                    try { return call.callRealMethod(); }
                    finally { connection.rollback(); }
                }
            }
        }).when(writer).write(org.mockito.ArgumentMatchers.any());
        long started = System.nanoTime();
        var login = mvc.perform(post("/api/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")))))
            .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        try {
            assertThat((System.nanoTime() - started) / 1_000_000_000).isLessThan(10);
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE request_id=?", Integer.class, login.getResponse().getHeader("X-Request-Id"))).isZero();
            assertThat(output.getOut()).contains("ERROR", "AUDIT_WRITE_FAILED", "requestId=" + login.getResponse().getHeader("X-Request-Id"));
        } finally { tokens.revoke(token); }
    }

    /** 仅测试上下文启用的提交后响应故障点，不新增生产接口。 */
    @org.springframework.web.bind.annotation.ControllerAdvice
    static class ResponseFailure implements org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<Object> {
        /** 限定当前上下文的响应序列化阶段。 */
        @Override public boolean supports(org.springframework.core.MethodParameter type, Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter) { return true; }
        /** 同一请求只抛出一次，允许统一异常响应正常编码。 */
        @Override public Object beforeBodyWrite(Object body, org.springframework.core.MethodParameter type, org.springframework.http.MediaType mediaType,
                Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter, org.springframework.http.server.ServerHttpRequest request,
                org.springframework.http.server.ServerHttpResponse response) {
            var servlet = ((org.springframework.http.server.ServletServerHttpRequest) request).getServletRequest();
            if ("once".equals(servlet.getHeader("X-Test-Response-Failure")) && servlet.getAttribute("test.responseFailed") == null) {
                servlet.setAttribute("test.responseFailed", true); throw new IllegalStateException("test response failure");
            }
            return body;
        }
    }
}
