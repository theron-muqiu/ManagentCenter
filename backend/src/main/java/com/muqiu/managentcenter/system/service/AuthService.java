package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.system.dto.ChangePasswordRequest;
import com.muqiu.managentcenter.system.dto.LoginRequest;
import com.muqiu.managentcenter.system.entity.UserAccount;
import com.muqiu.managentcenter.system.repository.UserRepository;
import com.muqiu.managentcenter.system.security.AuthPrincipal;
import com.muqiu.managentcenter.system.security.IssuedToken;
import com.muqiu.managentcenter.system.security.RedisTokenStore;
import com.muqiu.managentcenter.system.vo.LoginData;
import com.muqiu.managentcenter.system.vo.MeData;
import com.muqiu.managentcenter.system.vo.UserView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 认证签发与数据库提交协调，Redis 不参与数据库回滚。 */
@Service
public class AuthService {
    /** 安全运行日志。 */
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    /** 用户数据。 */
    private final UserRepository users;
    /** 密码校验。 */
    private final PasswordEncoder encoder;
    /** 随机凭据存储。 */
    private final RedisTokenStore tokens;
    /** 展示菜单。 */
    private final com.muqiu.managentcenter.system.service.PermissionService permissions;
    /** 明确提交边界。 */
    private final TransactionTemplate transaction;
    /** 未知账号也执行密码计算。 */
    private final String dummyHash;

    /** 注入认证依赖并准备固定 dummy 摘要。 */
    public AuthService(UserRepository users, PasswordEncoder encoder, RedisTokenStore tokens,
            com.muqiu.managentcenter.system.service.PermissionService permissions, PlatformTransactionManager manager) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.permissions = permissions;
        this.transaction = new TransactionTemplate(manager);
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    /** 锁定用户后复核并签发，提交失败尽力删除未返回的凭据。 */
    public LoginData login(LoginRequest request) {
        if (!request.username().equals(request.username().strip())) throw new ApiException(ErrorCode.INVALID_INPUT);
        IssuedToken[] issued = new IssuedToken[1];
        try {
            return transaction.execute(status -> {
                UserAccount found = users.findByUsername(request.username());
                if (found == null) {
                    encoder.matches(request.password(), dummyHash);
                    throw new ApiException(ErrorCode.LOGIN_FAILED);
                }
                UserAccount user = users.lockById(found.id());
                com.muqiu.managentcenter.system.audit.AuditOutcome.recognizedLoginUser(user);
                if (!encoder.matches(request.password(), user.passwordHash())) throw new ApiException(ErrorCode.LOGIN_FAILED);
                if (user.status() != com.muqiu.managentcenter.system.enums.UserStatus.ENABLED.code()) throw new ApiException(ErrorCode.USER_DISABLED);
                issued[0] = tokens.issue(user.id(), user.authVersion());
                com.muqiu.managentcenter.system.audit.AuditOutcome.committed(user.id());
                return new LoginData(users.view(user.id()), issued[0].info().expiresAt(), issued[0].token(), "Bearer");
            });
        } catch (RuntimeException exception) {
            if (issued[0] != null) {
                try { tokens.revoke(issued[0].token()); }
                catch (RuntimeException cleanup) { log.error("[认证管理] 登录补偿失败 exceptionType={}", cleanup.getClass().getSimpleName()); }
            }
            throw exception;
        }
    }

    /** 退出只撤销当前凭据。 */
    public void logout(AuthPrincipal principal) {
        tokens.revoke(principal.token());
        log.info("[认证管理] 退出完成 userId={}", principal.user().id());
    }

    /** 当前请求已读取最新权限，公开资料在一致快照内查询。 */
    @Transactional(readOnly = true)
    public MeData me(AuthPrincipal principal) {
        UserView user = users.view(principal.user().id());
        return new MeData(user, user.roles().stream().map(role -> role.id()).toList(),
                principal.codes().stream().sorted().toList(), permissions.menus(principal.codes()), principal.expiresAt());
    }

    /** 用户行锁内复核当前凭据和原密码，密码及全部登录撤销同事务提交。 */
    @Transactional
    public void changePassword(AuthPrincipal principal, ChangePasswordRequest request) {
        UserAccount user = users.lockById(principal.user().id());
        if (user.status() != com.muqiu.managentcenter.system.enums.UserStatus.ENABLED.code()
                || user.authVersion() != principal.user().authVersion()) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        if (!encoder.matches(request.oldPassword(), user.passwordHash())) throw new ApiException(ErrorCode.WRONG_OLD_PASSWORD);
        users.updatePassword(user.id(), encoder.encode(request.newPassword()));
        com.muqiu.managentcenter.system.audit.AuditOutcome.committed(user.id());
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                /** 只在真实提交后记录完成。 */
                @Override public void afterCommit() { log.info("[认证管理] 本人改密完成 userId={}", user.id()); }
            });
    }
}
