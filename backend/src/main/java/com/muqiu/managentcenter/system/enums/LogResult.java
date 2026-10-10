package com.muqiu.managentcenter.system.enums;

/** 记录结果，固定编码不可重排。 */
public enum LogResult {
    FAILURE(0), SUCCESS(1);

    /** 固定持久化与接口编码。 */
    private final int code;

    /** 保存显式编码。 */
    LogResult(int code) { this.code = code; }

    /** 返回持久化与接口编码。 */
    public int code() { return code; }
}
