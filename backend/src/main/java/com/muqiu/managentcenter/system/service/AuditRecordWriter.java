package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.audit.RequestAuditContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 独立新事务同步尽力写入；调用方隔离写入失败。 */
@Service
public class AuditRecordWriter {
    /** 数据库插入。 */ private final JdbcTemplate jdbc;
    /** 白名单 JSON 编码。 */ private final ObjectMapper mapper;
    /** 注入写入依赖。 */ public AuditRecordWriter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    /** 仅 INSERT 日志，业务事务已结束；语句期限由当前新事务控制。 */
    @Transactional(propagation=Propagation.REQUIRES_NEW, timeoutString="${audit.write-timeout-seconds}")
    public void write(RequestAuditContext context) {
        String reason = null;
        if (!context.committed) {
            reason = java.util.Arrays.stream(ErrorCode.values()).filter(code -> code.code() == context.errorCode).findFirst().orElse(ErrorCode.INTERNAL_ERROR).message();
        }
        LocalDateTime occurred = LocalDateTime.ofInstant(context.occurredAt, ZoneOffset.UTC);
        LocalDateTime created = LocalDateTime.now(ZoneOffset.UTC);
        if (context.login) {
            jdbc.update("INSERT INTO sys_login_log(user_id,attempted_username,request_id,occurred_at,result,error_code,failure_reason,client_ip,user_agent,duration_ms,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                context.loginUserId, context.attemptedUsername, context.requestId, occurred, context.committed ? com.muqiu.managentcenter.system.enums.LogResult.SUCCESS.code() : com.muqiu.managentcenter.system.enums.LogResult.FAILURE.code(),
                context.committed ? 0 : context.errorCode, reason, context.clientIp, context.userAgent, context.durationMs, created);
        } else {
            String summary;
            try { summary = mapper.writeValueAsString(context.summary); }
            catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException("审计安全摘要编码失败"); }
            jdbc.update("INSERT INTO sys_operation_log(operator_id,operator_username,operator_display_name,module,feature,operation_type,target_type,target_id,request_method,request_path,summary,request_id,occurred_at,result,error_code,failure_reason,client_ip,user_agent,duration_ms,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                context.operatorId, context.operatorUsername, context.operatorDisplayName, context.module, context.feature, context.operationType, context.targetType, context.targetId,
                context.method, context.path, summary, context.requestId, occurred, context.committed ? com.muqiu.managentcenter.system.enums.LogResult.SUCCESS.code() : com.muqiu.managentcenter.system.enums.LogResult.FAILURE.code(), context.committed ? 0 : context.errorCode,
                reason, context.clientIp, context.userAgent, context.durationMs, created);
        }
    }
}
