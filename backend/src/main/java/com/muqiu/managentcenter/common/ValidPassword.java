package com.muqiu.managentcenter.common;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验 BCrypt 的 UTF-8 字节容量，禁止静默截断。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface ValidPassword {
    /** 安全提示。 */
    String message() default "密码不能为空且 UTF-8 长度不得超过72字节";
    /** 校验分组。 */
    Class<?>[] groups() default {};
    /** 校验附加信息。 */
    Class<? extends Payload>[] payload() default {};
}
