package com.muqiu.managentcenter.system.audit;

import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.enums.OperationType;
import com.muqiu.managentcenter.system.enums.TargetType;
import com.muqiu.managentcenter.system.service.AuditRecordWriter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Security 前采集固定事件；完成后记录原业务实际结果。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditCaptureFilter extends OncePerRequestFilter {
    /** 安全告警事件。 */ private static final Logger log = LoggerFactory.getLogger(AuditCaptureFilter.class);
    /** 独立事务写入器。 */ private final AuditRecordWriter writer;
    /** 配置可信代理地址，不默认信任来源头。 */ private final List<String> trustedProxies;
    /** 注入写入器及代理配置。 */
    public AuditCaptureFilter(AuditRecordWriter writer, @Value("${audit.trusted-proxies:}") String trustedProxies) {
        this.writer = writer;
        this.trustedProxies = java.util.Arrays.stream(trustedProxies.split(",")).map(String::strip).filter(value -> !value.isEmpty()).toList();
    }
    /** 固定映射十个写操作和登录，未知原始路径不入库。 */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var context = new RequestAuditContext();
        request.setAttribute("audit.requestId", context.requestId);
        response.setHeader("X-Request-Id", context.requestId);
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        context.method = method;
        boolean captured = true;
        if (method.equals("POST") && path.equals("/api/auth/login")) { context.login = true; context.path = path; }
        else if (method.equals("PUT") && path.equals("/api/auth/password")) {
            context.module = "auth"; context.feature = "修改本人密码"; context.operationType = OperationType.CHANGE_PASSWORD.code(); context.targetType = TargetType.USER.code(); context.path = path;
        } else if (method.equals("POST") && path.equals("/api/users")) {
            context.module = "user"; context.feature = "新增用户"; context.operationType = OperationType.CREATE.code(); context.targetType = TargetType.USER.code(); context.path = path;
        } else if (method.equals("POST") && path.equals("/api/roles")) {
            context.module = "role"; context.feature = "新增角色"; context.operationType = OperationType.CREATE.code(); context.targetType = TargetType.ROLE.code(); context.path = path;
        } else {
            var match = java.util.regex.Pattern.compile("^/api/(users|roles)/([^/]+)(/(status|password|roles|permissions))?$").matcher(path);
            if (match.matches()) {
                boolean user = match.group(1).equals("users");
                String action = match.group(4);
                if (action == null && method.equals("PUT")) { context.operationType = OperationType.EDIT.code(); context.feature = user ? "编辑用户资料" : "编辑角色资料"; }
                else if (action == null && !user && method.equals("DELETE")) { context.operationType = OperationType.DELETE.code(); context.feature = "删除角色"; }
                else if (user && method.equals("PUT") && "status".equals(action)) { context.operationType = OperationType.STATUS.code(); context.feature = "调整用户状态"; }
                else if (user && method.equals("PUT") && "password".equals(action)) { context.operationType = OperationType.RESET_PASSWORD.code(); context.feature = "重置用户密码"; }
                else if (user && method.equals("PUT") && "roles".equals(action)) { context.operationType = OperationType.ASSIGN_ROLE.code(); context.feature = "分配用户角色"; }
                else if (!user && method.equals("PUT") && "permissions".equals(action)) { context.operationType = OperationType.ASSIGN_PERMISSION.code(); context.feature = "分配角色权限"; }
                else captured = false;
                if (captured) {
                    context.module = user ? "user" : "role"; context.targetType = user ? TargetType.USER.code() : TargetType.ROLE.code();
                    context.path = "/api/" + match.group(1) + "/{id}" + (action == null ? "" : "/" + action);
                    try { if (match.group(2).matches("[1-9][0-9]*")) context.targetId = Long.parseLong(match.group(2)); }
                    catch (NumberFormatException ignored) { /* 无效标识不保存原文。 */ }
                }
            } else captured = false;
        }
        if (!captured) { chain.doFilter(request, response); return; }
        context.clientIp = request.getRemoteAddr();
        if (trustedProxies.contains(context.clientIp)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && forwarded.length() <= 2048) {
                String[] hops = forwarded.split(",");
                for (int index = hops.length - 1; index >= 0; index--) {
                    String candidate = hops[index].strip();
                    if (!candidate.matches("[0-9a-fA-F:.]+")) break;
                    try { java.net.InetAddress.getByName(candidate); } catch (java.net.UnknownHostException invalid) { break; }
                    context.clientIp = candidate;
                    if (!trustedProxies.contains(candidate)) break;
                }
            }
        }
        String agent = request.getHeader("User-Agent");
        if (agent != null) {
            agent = agent.replaceAll("[\\p{Cntrl}]", "");
            context.userAgent = agent.substring(0, Math.min(512, agent.length()));
        }
        request.setAttribute("audit.context", context);
        try { chain.doFilter(request, response); }
        finally {
            context.durationMs = Math.max(0, (System.nanoTime() - context.startedNanos) / 1_000_000);
            Object error = request.getAttribute("audit.errorCode");
            context.errorCode = error instanceof Integer code ? code : switch (response.getStatus()) {
                case 400 -> ErrorCode.INVALID_INPUT.code(); case 401 -> ErrorCode.UNAUTHENTICATED.code();
                case 403 -> ErrorCode.FORBIDDEN.code(); case 503 -> ErrorCode.REDIS_UNAVAILABLE.code(); default -> ErrorCode.INTERNAL_ERROR.code();
            };
            try { writer.write(context); }
            catch (RuntimeException failure) {
                log.error("AUDIT_WRITE_FAILED logType={} requestId={} operatorId={} targetId={} reason=STORE_FAILURE exceptionType={}",
                    context.login ? "login" : "operation", context.requestId, context.operatorId, context.targetId, failure.getClass().getSimpleName());
            }
        }
    }
}
