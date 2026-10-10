package com.muqiu.managentcenter.system.security;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.entity.UserAccount;
import com.muqiu.managentcenter.system.enums.UserStatus;
import com.muqiu.managentcenter.system.repository.PermissionRepository;
import com.muqiu.managentcenter.system.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每个受保护请求重新核对凭据、账号及有效授权。 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    /** 凭据存储。 */ private final RedisTokenStore tokens;
    /** 用户查询。 */ private final UserRepository users;
    /** 即时权限查询。 */ private final PermissionRepository permissions;
    /** 安全响应编码。 */ private final ObjectMapper mapper;
    /** 注入当前请求认证依赖。 */
    public TokenAuthenticationFilter(RedisTokenStore tokens, UserRepository users, PermissionRepository permissions, ObjectMapper mapper) {
        this.tokens = tokens; this.users = users; this.permissions = permissions; this.mapper = mapper;
    }
    /** 身份校验失败立即拒绝，不把下游业务异常误认成认证失败。 */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (request.getParameterMap().keySet().stream().anyMatch(name -> java.util.Set.of("token", "access_token", "accesstoken", "authorization").contains(name.toLowerCase(java.util.Locale.ROOT))))
                throw new ApiException(ErrorCode.INVALID_INPUT);
            boolean login = request.getMethod().equals("POST") && request.getRequestURI().substring(request.getContextPath().length()).equals("/api/auth/login");
            if (!login) {
                String header = request.getHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ") || java.util.Collections.list(request.getHeaders("Authorization")).size() != 1)
                    throw new ApiException(ErrorCode.UNAUTHENTICATED);
                String token = header.substring(7);
                TokenInfo info = tokens.find(token);
                long id;
                long version;
                try { id = Long.parseLong(info.userId()); version = Long.parseLong(info.authVersion()); }
                catch (NumberFormatException exception) { throw new ApiException(ErrorCode.UNAUTHENTICATED); }
                if (id <= 0 || version < 0) throw new ApiException(ErrorCode.UNAUTHENTICATED);
                UserAccount user;
                try { user = users.findById(id); }
                catch (ApiException exception) { throw new ApiException(ErrorCode.UNAUTHENTICATED); }
                if (user.status() != UserStatus.ENABLED.code() || user.authVersion() != version) throw new ApiException(ErrorCode.UNAUTHENTICATED);
                AuthPrincipal principal = new AuthPrincipal(user, permissions.effectiveCodes(id, user.superAdmin()), token, info.expiresAt());
                if (request.getAttribute("audit.context") instanceof com.muqiu.managentcenter.system.audit.RequestAuditContext context) {
                    context.operatorId = user.id(); context.operatorUsername = user.username(); context.operatorDisplayName = user.displayName();
                    if (context.path.equals("/api/auth/password")) context.targetId = user.id();
                }
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
            }
        } catch (RuntimeException exception) {
            ErrorCode error = exception instanceof ApiException api ? api.error() : ErrorCode.INTERNAL_ERROR;
            request.setAttribute("audit.errorCode", error.code());
            response.setStatus(error.httpStatus());
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), ApiResponse.failure(error));
            return;
        }
        chain.doFilter(request, response);
    }
}
