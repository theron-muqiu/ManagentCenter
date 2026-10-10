package com.muqiu.managentcenter.system.security;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.config.AuthProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 是唯一凭据存储，保存随机 Token 的摘要，不保存权限快照。 */
@Component
public class RedisTokenStore {
    /** 只记录补偿失败类型，不输出凭据键。 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisTokenStore.class);
    /** 随机数生成器。 */
    private final SecureRandom random = new SecureRandom();
    /** Redis 访问。 */
    private final StringRedisTemplate redis;
    /** 受控 JSON。 */
    private final ObjectMapper mapper;
    /** 明确期限。 */
    private final AuthProperties properties;

    /** 注入存储与序列化配置。 */
    public RedisTokenStore(StringRedisTemplate redis, ObjectMapper mapper, AuthProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 只签发唯一随机键，原子附加 TTL。 */
    public IssuedToken issue(long userId, long authVersion) {
        for (int attempt = 0; attempt < 3; attempt++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Instant created = Instant.now();
            TokenInfo info = new TokenInfo(Long.toString(userId), Long.toString(authVersion), created, created.plus(properties.tokenTtl()));
            try {
                String json = mapper.writeValueAsString(info);
                if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(token), json, properties.tokenTtl()))) {
                    return new IssuedToken(token, info);
                }
            } catch (DataAccessException exception) {
                // 超时可能已经写入，尽力清理；不得向客户端返回未知签发结果。
                try { redis.delete(key(token)); } catch (DataAccessException cleanup) {
                    log.error("[认证管理] 凭据清理失败 exceptionType={}", cleanup.getClass().getSimpleName());
                }
                throw new ApiException(ErrorCode.REDIS_UNAVAILABLE);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR);
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    /** 校验键存在及安全结构，Redis 故障不降级放行。 */
    public TokenInfo find(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        try {
            String json = redis.opsForValue().get(key(token));
            if (json == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
            TokenInfo info = mapper.readValue(json, TokenInfo.class);
            if (info == null || info.userId() == null || info.authVersion() == null || info.createdAt() == null || info.expiresAt() == null
                    || !info.expiresAt().isAfter(Instant.now())) throw new ApiException(ErrorCode.UNAUTHENTICATED);
            return info;
        } catch (DataAccessException exception) {
            throw new ApiException(ErrorCode.REDIS_UNAVAILABLE);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /** 删除当前凭据，失败明确返回而不能伪称退出。 */
    public void revoke(String token) {
        try {
            redis.delete(key(token));
        } catch (DataAccessException exception) {
            throw new ApiException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }

    /** 单向摘要构成内部键，禁止输出该键。 */
    private String key(String token) {
        try {
            return "rbac:token:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用");
        }
    }
}
