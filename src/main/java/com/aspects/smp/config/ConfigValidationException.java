package com.aspects.smp.config;

import java.util.List;

public final class ConfigValidationException extends Exception {
    private final List<String> errors;

    public ConfigValidationException(List<String> errors) {
        super("Configuration validation failed: " + String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
