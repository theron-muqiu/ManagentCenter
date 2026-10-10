package com.muqiu.managentcenter.common;

/** 对外接口统一包装，禁止返回持久化实体。 */
public record ApiResponse<T>(
        /** 结果编码，成功为零。 */
        int code,
        /** 安全的结果说明。 */
        String message,
        /** 对外业务数据。 */
        T data) {

    /** 构造成功响应。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /** 构造安全错误响应。 */
    public static ApiResponse<Void> failure(ErrorCode error) {
        return new ApiResponse<>(error.code(), error.message(), null);
    }
}
