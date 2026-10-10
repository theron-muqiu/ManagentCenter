package com.muqiu.managentcenter.common;

/** 可预期的业务失败，不携带敏感请求值。 */
public class ApiException extends RuntimeException {
    /** 对外错误。 */
    private final ErrorCode error;

    /** 创建固定错误。 */
    public ApiException(ErrorCode error) {
        super(error.message());
        this.error = error;
    }

    /** 返回业务错误。 */
    public ErrorCode error() { return error; }
}
