package com.muqiu.managentcenter.system.config;

import com.muqiu.managentcenter.common.ApiResponse;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.repository.PermissionRepository;
import com.muqiu.managentcenter.system.repository.UserRepository;
import com.muqiu.managentcenter.system.security.RbacAuthorizationManager;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.security.TokenAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 显式无状态 Bearer 安全链。 */
@Configuration
public class SecurityConfiguration {
    /** 定义认证、默认拒绝和统一错误响应，禁用浏览器自动认证方式。 */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RedisTokenStore tokens, UserRepository users,
            PermissionRepository permissions, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().access(new RbacAuthorizationManager()))
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> {
                        request.setAttribute("audit.errorCode", ErrorCode.UNAUTHENTICATED.code());
                        response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ErrorCode.UNAUTHENTICATED));
                    })
                    .accessDeniedHandler((request, response, exception) -> {
                        request.setAttribute("audit.errorCode", ErrorCode.FORBIDDEN.code());
                        response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ErrorCode.FORBIDDEN));
                    }))
                .addFilterBefore(new TokenAuthenticationFilter(tokens, users, permissions, mapper), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
