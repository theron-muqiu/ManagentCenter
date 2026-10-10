package com.muqiu.managentcenter.system.audit;

import com.muqiu.managentcenter.system.dto.CreateRoleRequest;
import com.muqiu.managentcenter.system.dto.CreateUserRequest;
import com.muqiu.managentcenter.system.dto.LoginRequest;
import com.muqiu.managentcenter.system.dto.RolePermissionsRequest;
import com.muqiu.managentcenter.system.dto.StatusRequest;
import com.muqiu.managentcenter.system.dto.UpdateRoleRequest;
import com.muqiu.managentcenter.system.dto.UpdateUserRequest;
import com.muqiu.managentcenter.system.dto.UserRolesRequest;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.context.request.*;

/** 仅提取允许字段，不缓存或序列化原始请求对象。 */
@ControllerAdvice
public class AuditBodyAdvice extends RequestBodyAdviceAdapter {
    /** 只在存在采集上下文时处理 DTO。 */
    @Override public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converter) {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute("audit.context") instanceof RequestAuditContext;
    }
    /** 安全字段独立校验，非法 DTO 也不落敏感值。 */
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converter) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        var context = (RequestAuditContext) attributes.getRequest().getAttribute("audit.context");
        if (body instanceof LoginRequest request) {
            String username = request.username();
            if (username != null && !username.isBlank() && username.length() <= 64 && username.equals(username.strip()) && username.chars().noneMatch(Character::isISOControl))
                context.attemptedUsername = username;
        } else if (body instanceof CreateUserRequest request) {
            if (request.username() != null && !request.username().isBlank() && request.username().length() <= 64 && request.username().equals(request.username().strip())) context.summary.put("username", request.username());
            if (request.displayName() != null && !request.displayName().isBlank() && request.displayName().length() <= 100) context.summary.put("displayName", request.displayName());
            context.summary.put("changedFields", java.util.List.of("username", "displayName", "phone", "email"));
        } else if (body instanceof UpdateUserRequest request) {
            if (request.displayName() != null && !request.displayName().isBlank() && request.displayName().length() <= 100) context.summary.put("displayName", request.displayName());
            context.summary.put("changedFields", java.util.List.of("displayName", "phone", "email"));
        } else if (body instanceof StatusRequest request) {
            if (request.status() != null && request.status() >= 0 && request.status() <= 1) context.summary.put("status", request.status());
        } else if (body instanceof UserRolesRequest request) {
            if (request.roleIds() != null && request.roleIds().stream().allMatch(id -> id != null && id.matches("[1-9][0-9]{0,18}") && new java.math.BigInteger(id).compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0))
                context.summary.put("roleIds", request.roleIds().stream().distinct().toList());
        } else if (body instanceof RolePermissionsRequest request) {
            if (request.permissionIds() != null && request.permissionIds().stream().allMatch(id -> id != null && id.matches("[1-9][0-9]{0,18}") && new java.math.BigInteger(id).compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0))
                context.summary.put("permissionIds", request.permissionIds().stream().distinct().toList());
        } else if (body instanceof CreateRoleRequest || body instanceof UpdateRoleRequest) {
            context.summary.put("changedFields", java.util.List.of("name", "description"));
        }
        return body;
    }
}
