package com.muqiu.managentcenter.system.enums;

/** 权限类型，固定编码不可重排。 */
public enum PermissionType {
    MENU(0), PAGE(1), ACTION(2);

    /** 固定持久化与接口编码。 */
    private final int code;

    /** 保存显式编码。 */
    PermissionType(int code) { this.code = code; }

    /** 返回持久化与接口编码。 */
    public int code() { return code; }
}
