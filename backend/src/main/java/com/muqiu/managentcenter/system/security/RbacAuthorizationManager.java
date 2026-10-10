package com.muqiu.managentcenter.system.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** 固定接口映射，未知入口默认拒绝。 */
public class RbacAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    /** 精确按方法、路径和独立操作权限判断。 */
    @Override public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        var request = context.getRequest();
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (method.equals("POST") && path.equals("/api/auth/login")) return new AuthorizationDecision(true);
        Authentication auth = authentication.get();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) return new AuthorizationDecision(false);
        if ((method.equals("GET") && path.equals("/api/auth/me")) || (method.equals("POST") && path.equals("/api/auth/logout"))
                || (method.equals("PUT") && path.equals("/api/auth/password"))) return new AuthorizationDecision(true);
        String code = null;
        if (path.equals("/api/users")) code = switch (method) { case "GET" -> "user:query"; case "POST" -> "user:create"; default -> null; };
        else if (path.matches("/api/users/[^/]+")) code = switch (method) { case "GET" -> "user:query"; case "PUT" -> "user:edit"; default -> null; };
        else if (path.matches("/api/users/[^/]+/status") && method.equals("PUT")) code = "user:status";
        else if (path.matches("/api/users/[^/]+/password") && method.equals("PUT")) code = "user:reset-password";
        else if (path.matches("/api/users/[^/]+/roles") && (method.equals("GET") || method.equals("PUT"))) code = "user:assign-role";
        else if (path.equals("/api/roles")) code = switch (method) { case "GET" -> "role:query"; case "POST" -> "role:create"; default -> null; };
        else if (path.matches("/api/roles/[^/]+")) code = switch (method) { case "GET" -> "role:query"; case "PUT" -> "role:edit"; case "DELETE" -> "role:delete"; default -> null; };
        else if (path.matches("/api/roles/[^/]+/permissions") && (method.equals("GET") || method.equals("PUT"))) code = "role:assign-permission";
        else if (path.equals("/api/permissions/tree") && method.equals("GET")) code = "role:assign-permission";
        else if (path.equals("/api/role-options") && method.equals("GET")) code = "user:assign-role";
        else if ((path.equals("/api/operation-logs") || path.matches("/api/operation-logs/[^/]+")) && method.equals("GET")) code = "operation-log:query";
        else if ((path.equals("/api/login-logs") || path.matches("/api/login-logs/[^/]+")) && method.equals("GET")) code = "login-log:query";
        return new AuthorizationDecision(code != null && principal.codes().contains(code));
    }
}
