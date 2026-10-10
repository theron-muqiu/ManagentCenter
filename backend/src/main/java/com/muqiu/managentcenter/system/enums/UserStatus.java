package com.muqiu.managentcenter.system.enums;

/** 用户状态，固定编码不可重排。 */
public enum UserStatus {
    DISABLED(0), ENABLED(1);

    /** 固定持久化与接口编码。 */
    private final int code;

    /** 保存显式编码。 */
    UserStatus(int code) { this.code = code; }

    /** 返回持久化与接口编码。 */
    public int code() { return code; }
}
