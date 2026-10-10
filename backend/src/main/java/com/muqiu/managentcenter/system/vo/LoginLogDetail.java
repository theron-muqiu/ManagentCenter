package com.muqiu.managentcenter.system.vo;

import java.time.Instant;

/** LoginLogDetail 对外只读历史字段。 */
public record LoginLogDetail(
        /** 记录标识。 */ String id,
        /** 服务端请求 UUID。 */ String requestId,
        /** 请求到达时间 UTC。 */ Instant occurredAt,
        /** 固定结果编码。 */ int result,
        /** 安全结果码。 */ int errorCode,
        /** 安全失败说明。 */ String failureReason,
        /** 安全来源 IP。 */ String clientIp,
        /** 主流程耗时毫秒。 */ long durationMs,
        /** 匹配用户逻辑引用。 */ String userId,
        /** 安全尝试账号。 */ String attemptedUsername,
        /** 已清理浏览器信息。 */ String userAgent,
        /** 写入时间 UTC。 */ Instant createdAt) {}

