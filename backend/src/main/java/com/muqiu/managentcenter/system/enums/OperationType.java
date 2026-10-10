package com.muqiu.managentcenter.system.enums;

/** 操作类型，固定编码不可重排。 */
public enum OperationType {
    CREATE(0), EDIT(1), STATUS(2), DELETE(3), CHANGE_PASSWORD(4), RESET_PASSWORD(5), ASSIGN_ROLE(6), ASSIGN_PERMISSION(7);

    /** 固定持久化与接口编码。 */
    private final int code;

    /** 保存显式编码。 */
    OperationType(int code) { this.code = code; }

    /** 返回持久化与接口编码。 */
    public int code() { return code; }
}
