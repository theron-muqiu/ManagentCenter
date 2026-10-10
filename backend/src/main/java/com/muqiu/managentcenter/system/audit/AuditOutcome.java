package com.muqiu.managentcenter.system.audit;

import com.muqiu.managentcenter.system.entity.UserAccount;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.transaction.support.*;

/** 业务真实提交信号，不能通过响应状态猜测数据库提交。 */
public final class AuditOutcome {
    /** 不实例化提交桥接组件。 */ private AuditOutcome() {}
    /** 原事务 afterCommit 回调更新请求快照，直接测试调用不创建审计事件。 */
    public static void committed(Long targetId) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        Object value = attributes.getRequest().getAttribute("audit.context");
        if (!(value instanceof RequestAuditContext context)) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("业务提交信号必须来自事务");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 只在真实数据库提交后确定成功及新建标识。 */
            @Override public void afterCommit() {
                context.committed = true;
                if (context.login) context.loginUserId = targetId;
                else if (targetId != null) context.targetId = targetId;
            }
        });
    }
    /** 登录账号已识别可记录逻辑引用，但不伪造已认证操作人。 */
    public static void recognizedLoginUser(UserAccount user) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        if (attributes.getRequest().getAttribute("audit.context") instanceof RequestAuditContext context && context.login) context.loginUserId = user.id();
    }
}
