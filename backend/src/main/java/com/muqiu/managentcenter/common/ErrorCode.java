package com.muqiu.managentcenter.common;

/** 固定接口错误码及 HTTP 状态，不依赖枚举序号。 */
public enum ErrorCode {
    INVALID_INPUT(40001, 400, "参数错误"),
    WRONG_OLD_PASSWORD(40002, 400, "原密码错误"),
    UNAUTHENTICATED(40101, 401, "登录凭证无效或已失效"),
    LOGIN_FAILED(40102, 401, "账号或密码错误"),
    FORBIDDEN(40301, 403, "无操作权限"),
    USER_DISABLED(40302, 403, "账号已停用"),
    NOT_FOUND(40401, 404, "数据不存在"),
    DUPLICATE(40901, 409, "账号或角色名称重复"),
    CONFLICT(40902, 409, "数据已变化，请刷新后重试"),
    ROLE_IN_USE(40903, 409, "角色已分配用户，请先解除关联"),
    SUPER_ADMIN_PROTECTED(40904, 409, "超级管理员不可停用"),
    INTERNAL_ERROR(50000, 500, "服务暂时不可用"),
    REDIS_UNAVAILABLE(50301, 503, "认证服务暂时不可用");

    /** 稳定结果码。 */
    private final int code;
    /** HTTP 状态。 */
    private final int httpStatus;
    /** 安全展示文案。 */
    private final String message;

    /** 固定枚举契约。 */
    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /** 返回结果码。 */
    public int code() { return code; }
    /** 返回 HTTP 状态。 */
    public int httpStatus() { return httpStatus; }
    /** 返回安全文案。 */
    public String message() { return message; }
}
