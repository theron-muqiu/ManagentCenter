package com.muqiu.managentcenter.system.enums;

/** 操作目标类型，固定编码不可重排。 */
public enum TargetType {
    USER(0), ROLE(1);

    /** 固定持久化与接口编码。 */
    private final int code;

    /** 保存显式编码。 */
    TargetType(int code) { this.code = code; }

    /** 返回持久化与接口编码。 */
    public int code() { return code; }
}
