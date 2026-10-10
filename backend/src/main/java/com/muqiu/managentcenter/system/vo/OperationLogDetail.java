package com.muqiu.managentcenter.system.vo;

import java.time.Instant;
import com.fasterxml.jackson.databind.JsonNode;

/** OperationLogDetail 对外只读历史字段。 */
public record OperationLogDetail(
        /** 记录标识。 */ String id,
        /** 服务端请求 UUID。 */ String requestId,
        /** 请求到达时间 UTC。 */ Instant occurredAt,
        /** 固定结果编码。 */ int result,
        /** 安全结果码。 */ int errorCode,
        /** 安全失败说明。 */ String failureReason,
        /** 安全来源 IP。 */ String clientIp,
        /** 主流程耗时毫秒。 */ long durationMs,
        /** 真实操作人 ID，可为空。 */ String operatorId,
        /** 操作账号快照。 */ String operatorUsername,
        /** 显示名称快照。 */ String operatorDisplayName,
        /** 固定模块代码。 */ String module,
        /** 固定功能名称。 */ String feature,
        /** 固定操作类型编码。 */ int operationType,
        /** 固定目标类型编码。 */ Integer targetType,
        /** 目标逻辑引用。 */ String targetId,
        /** 固定请求方法。 */ String requestMethod,
        /** 固定路径模板。 */ String requestPath,
        /** 白名单摘要对象。 */ JsonNode summary,
        /** 已清理浏览器信息。 */ String userAgent,
        /** 记录写入时间 UTC。 */ Instant createdAt) {}

