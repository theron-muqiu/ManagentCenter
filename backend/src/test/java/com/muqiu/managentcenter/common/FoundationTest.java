package com.muqiu.managentcenter.common;

import com.muqiu.managentcenter.system.config.AuthProperties;
import com.muqiu.managentcenter.system.enums.LogResult;
import com.muqiu.managentcenter.system.enums.OperationType;
import com.muqiu.managentcenter.system.enums.PermissionType;
import com.muqiu.managentcenter.system.enums.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 基础契约测试关注字节容量、固定编码和非法配置。 */
class FoundationTest {
    /** 多字节密码不得绕过 BCrypt 上限。 */
    @Test
    void passwordUsesUtf8ByteLimit() {
        PasswordValidator validator = new PasswordValidator();
        assertThat(validator.isValid("密".repeat(24), null)).isTrue();
        assertThat(validator.isValid("密".repeat(25), null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
    }

    /** 零和负数认证期限不允许启动。 */
    @Test
    void rejectsInvalidTtl() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new AuthProperties(Duration.ZERO))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties(Duration.ofSeconds(-1)))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties(null))).isNotEmpty();
            assertThat(validator.validate(new AuthProperties(Duration.ofHours(2)))).isEmpty();
        }
    }

    /** 包装和固定状态编码保持契约，不输出字符串枚举。 */
    @Test
    void serializesResponseAndStableCodes() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.success(UserStatus.ENABLED.code()));
        assertThat(json).isEqualTo("{\"code\":0,\"message\":\"success\",\"data\":1}");
        assertThat(LogResult.FAILURE.code()).isZero();
        assertThat(OperationType.ASSIGN_PERMISSION.code()).isEqualTo(7);
        assertThat(PermissionType.ACTION.code()).isEqualTo(2);
    }
}
