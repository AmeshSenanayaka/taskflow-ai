package com.taskflow.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // @NotBlank is responsible for null/blank; this validator only checks the format.
            return true;
        }
        return ZoneId.getAvailableZoneIds().contains(value);
    }
}
