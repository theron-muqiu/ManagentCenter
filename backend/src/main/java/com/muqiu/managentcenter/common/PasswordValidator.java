package com.muqiu.managentcenter.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** 密码校验只判断规则，不输出密码。 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    /** 校验非空及真实 UTF-8 字节数。 */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && !value.isEmpty()
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
