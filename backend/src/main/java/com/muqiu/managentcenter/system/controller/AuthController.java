package com.muqiu.managentcenter.system.controller;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.system.dto.ChangePasswordRequest;
import com.muqiu.managentcenter.system.dto.LoginRequest;
import com.muqiu.managentcenter.system.security.AuthPrincipal;
import com.muqiu.managentcenter.system.service.AuthService;
import com.muqiu.managentcenter.system.vo.LoginData;
import com.muqiu.managentcenter.system.vo.MeData;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 登录和当前身份接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    /** 入口运行日志。 */
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    /** 认证业务。 */
    private final AuthService service;
    /** 注入认证业务。 */
    public AuthController(AuthService service) { this.service = service; }
    /** 验证账号密码并返回本次凭据，禁止记录请求对象。 */
    @PostMapping("/login")
    public ApiResponse<LoginData> login(@Valid @RequestBody LoginRequest request) {
        log.info("[认证管理] 登录 username={}", request.username().replaceAll("[\\r\\n\\t]", ""));
        LoginData data = service.login(request);
        log.info("[认证管理] 登录完成 userId={}", data.user().id());
        return ApiResponse.success(data);
    }
    /** 退出当前登录。 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        log.info("[认证管理] 退出 userId={}", principal.user().id());
        service.logout(principal);
        return ApiResponse.success(null);
    }
    /** 获取身份及有效权限。 */
    @GetMapping("/me")
    public ApiResponse<MeData> me(@AuthenticationPrincipal AuthPrincipal principal) {
        log.info("[认证管理] 查询身份 userId={}", principal.user().id());
        return ApiResponse.success(service.me(principal));
    }
    /** 登录用户验证原密码后修改本人密码。 */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ChangePasswordRequest request) {
        log.info("[认证管理] 修改本人密码 userId={}", principal.user().id());
        service.changePassword(principal, request);
        return ApiResponse.success(null);
    }
}
