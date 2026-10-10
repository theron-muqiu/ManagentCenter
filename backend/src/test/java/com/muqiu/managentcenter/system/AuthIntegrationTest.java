package com.muqiu.managentcenter.system;

import com.muqiu.managentcenter.system.config.AuthProperties;
import com.muqiu.managentcenter.system.dto.ChangePasswordRequest;
import com.muqiu.managentcenter.system.dto.LoginRequest;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.service.AuthService;
import com.muqiu.managentcenter.system.vo.LoginData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** 真实 MySQL/Redis 与完整安全链验证，不替代前端组件验收。 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuthIntegrationTest {
    /** 接口执行。 */ @Autowired private MockMvc mvc;
    /** JSON 编码。 */ @Autowired private ObjectMapper mapper;
    /** 数据库。 */ @Autowired private JdbcTemplate jdbc;
    /** 密码编码。 */ @Autowired private PasswordEncoder encoder;
    /** 凭据签发。 */ @Autowired private RedisTokenStore tokens;
    /** 专用 Redis。 */ @Autowired private org.springframework.data.redis.core.StringRedisTemplate redis;
    /** 精确故障注入验证真实数据库回滚。 */
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private com.muqiu.managentcenter.system.repository.UserRepository users;
    /** 并发业务入口。 */ @Autowired private AuthService service;
    /** 验证隔离环境。 */
    @BeforeAll static void guard() {
        if (System.getenv("DB_URL") == null || !System.getenv("DB_URL").contains("/rbac_test"))
            throw new IllegalStateException("必须使用专用测试数据库");
    }
    /** 正确管理员登录，两份凭据可共存，退出仅影响当前凭据。 */
    @Test void loginAndLogoutAreIndependent() throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", System.getenv("BOOTSTRAP_USERNAME"), "password", System.getenv("BOOTSTRAP_PASSWORD")));
        var first = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        var second = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        String a = mapper.readTree(first.getResponse().getContentAsString()).path("data").path("token").asText();
        String b = mapper.readTree(second.getResponse().getContentAsString()).path("data").path("token").asText();
        try {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCodes.length()").value(15)).andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + a)).andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + a)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + b)).andExpect(status().isOk());
        } finally { tokens.revoke(a); tokens.revoke(b); }
    }
    /** 未认证、未知入口和非法输入都不能绕过安全链。 */
    @Test void rejectsMissingCredentialsAndInvalidLogin() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"unknown\",\"password\":\"wrong\",\"superAdmin\":true}"))
            .andExpect(status().isBadRequest());
    }
    /** 无角色账号可登录，修改认证版本后全部旧凭据失效。 */
    @Test void versionRevokesEveryTokenAndDefaultDenies() throws Exception {
        String username = "auth_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,?,?,1,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username, "认证测试", encoder.encode(password));
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        var a = tokens.issue(id, 0); var b = tokens.issue(id, 0);
        try {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + a.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionCodes.length()").value(0));
            mvc.perform(get("/api/users").header("Authorization", "Bearer " + a.token())).andExpect(status().isForbidden());
            mvc.perform(get("/api/unknown").header("Authorization", "Bearer " + a.token())).andExpect(status().isForbidden());
            jdbc.update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=?", id);
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + a.token())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + b.token())).andExpect(status().isUnauthorized());
        } finally { tokens.revoke(a.token()); tokens.revoke(b.token()); jdbc.update("DELETE FROM sys_user WHERE id=?", id); }
    }
    /** Redis 原子写入必带期限，值只保存认证元数据。 */
    @Test void tokenHasTtlAndNoSecrets() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var issued = tokens.issue(id, 0);
        try {
            assertThat(issued.token().length()).isEqualTo(43);
            assertThat(Duration.between(issued.info().createdAt(), issued.info().expiresAt()).isPositive()).isTrue();
            assertThat(tokens.find(issued.token()).userId()).isEqualTo(Long.toString(id));
            String key = "rbac:token:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(issued.token().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(redis.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS)).isPositive();
            String json = redis.opsForValue().get(key);
            assertThat(mapper.readTree(json).size()).isEqualTo(4);
            redis.opsForValue().set(key, "null", Duration.ofSeconds(5));
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + issued.token())).andExpect(status().isUnauthorized());
        } finally { tokens.revoke(issued.token()); }
    }
    /** 过期键和依赖故障都不降级为历史身份。 */
    @Test void expiryAndRedisFailureReject() throws Exception {
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE is_super_admin=1", Long.class);
        var shortStore = new RedisTokenStore(redis, mapper, new AuthProperties(Duration.ofMillis(100)));
        var issued = shortStore.issue(id, 0);
        Thread.sleep(160);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + issued.token())).andExpect(status().isUnauthorized());
        var failedRedis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.mockito.Mockito.when(failedRedis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("test fault"));
        var failedStore = new RedisTokenStore(failedRedis, mapper, new AuthProperties(Duration.ofMinutes(1)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> failedStore.find(issued.token()))
            .isInstanceOf(com.muqiu.managentcenter.common.ApiException.class)
            .satisfies(error -> assertThat(((com.muqiu.managentcenter.common.ApiException) error).error()).isEqualTo(com.muqiu.managentcenter.common.ErrorCode.REDIS_UNAVAILABLE));
    }
    /** 本人改密原子撤销两份凭据，输入及数据库失败不改变密码版本。 */
    @Test void passwordChangeRevokesOnlyAfterCommit() throws Exception {
        String username = "password_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = java.util.UUID.randomUUID().toString();
        String replacement = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,?,?,1,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username, "改密测试", encoder.encode(password));
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        var a = tokens.issue(id, 0); var b = tokens.issue(id, 0);
        try {
            mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", "wrong", "newPassword", replacement))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40002));
            mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", password, "newPassword", replacement, "userId", "1"))))
                .andExpect(status().isBadRequest());
            org.mockito.Mockito.doAnswer(call -> { call.callRealMethod(); throw new org.springframework.dao.DataAccessResourceFailureException("test fault"); })
                .when(users).updatePassword(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.anyString());
            mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", password, "newPassword", replacement)))).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT auth_version FROM sys_user WHERE id=?", Long.class, id)).isZero();
            assertThat(encoder.matches(password, jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?", String.class, id))).isTrue();
            org.mockito.Mockito.doCallRealMethod().when(users).updatePassword(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.anyString());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + b.token())).andExpect(status().isOk());
            mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("oldPassword", password, "newPassword", replacement)))).andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + a.token())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + b.token())).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized());
            var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", username, "password", replacement))))
                .andExpect(status().isOk()).andReturn();
            tokens.revoke(mapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText());
        } finally { tokens.revoke(a.token()); tokens.revoke(b.token()); jdbc.update("DELETE FROM sys_user WHERE id=?", id); }
    }
    /** 登录与改密竞争时，旧密码不会在改密提交后留下有效凭据。 */
    @Test void concurrentLoginAndPasswordChangeAreSerialized() throws Exception {
        String username = "race_test_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String password = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sys_user(username,display_name,password_hash,status,is_super_admin,version,auth_version,created_at,updated_at) VALUES(?,?,?,1,0,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", username, "并发测试", encoder.encode(password));
        long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        var current = tokens.issue(id, 0);
        var principal = new com.muqiu.managentcenter.system.security.AuthPrincipal(users.findById(id), java.util.Set.of(), current.token(), current.info().expiresAt());
        var gate = new java.util.concurrent.CountDownLatch(1);
        LoginData oldLogin = null;
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> {
                gate.await();
                try { return service.login(new LoginRequest(username, password)); }
                catch (com.muqiu.managentcenter.common.ApiException failure) {
                    assertThat(failure.error()).isEqualTo(com.muqiu.managentcenter.common.ErrorCode.LOGIN_FAILED);
                    return null;
                }
            });
            var change = executor.submit(() -> { gate.await(); service.changePassword(principal, new ChangePasswordRequest(password, "replacement-fixture")); return true; });
            gate.countDown();
            assertThat(change.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            oldLogin = login.get(20, java.util.concurrent.TimeUnit.SECONDS);
            if (oldLogin != null) mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldLogin.token())).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isUnauthorized());
        } finally { if (oldLogin != null) tokens.revoke(oldLogin.token()); tokens.revoke(current.token()); jdbc.update("DELETE FROM sys_user WHERE id=?", id); }
    }
}
