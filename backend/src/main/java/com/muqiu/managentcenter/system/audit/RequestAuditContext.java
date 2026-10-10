package com.muqiu.managentcenter.system.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 单请求安全快照，不持有原始请求、Token 或密码字段。 */
public class RequestAuditContext {
    /** 服务端请求 UUID。 */ public final String requestId = java.util.UUID.randomUUID().toString();
    /** 请求到达时间。 */ public final Instant occurredAt = Instant.now();
    /** 主流程单调时钟。 */ public final long startedNanos = System.nanoTime();
    /** 登录事件标记。 */ public boolean login;
    /** 固定模块。 */ public String module;
    /** 固定功能名称。 */ public String feature;
    /** 固定操作编码。 */ public int operationType;
    /** 目标类型，0 用户、1 角色。 */ public Integer targetType;
    /** 安全目标标识。 */ public Long targetId;
    /** 固定方法。 */ public String method;
    /** 固定路径模板。 */ public String path;
    /** 真实认证操作人。 */ public Long operatorId;
    /** 操作账号快照。 */ public String operatorUsername;
    /** 操作显示名称快照。 */ public String operatorDisplayName;
    /** 格式可接受的尝试登录账号。 */ public String attemptedUsername;
    /** 已识别登录用户。 */ public Long loginUserId;
    /** 安全来源地址。 */ public String clientIp;
    /** 已清理浏览器信息。 */ public String userAgent;
    /** 已通过白名单筛选的摘要。 */ public final Map<String, Object> summary = new LinkedHashMap<>();
    /** 实际提交结果。 */ public boolean committed;
    /** 实际错误码。 */ public int errorCode;
    /** 主流程耗时，排除日志写入。 */ public long durationMs;
}
