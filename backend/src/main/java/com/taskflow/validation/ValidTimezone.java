package com.taskflow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a known IANA timezone identifier (e.g. {@code Asia/Colombo}).
 */
@Documented
@Constraint(validatedBy = TimezoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimezone {

    String message() default "Timezone must be a valid IANA identifier such as Asia/Colombo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
