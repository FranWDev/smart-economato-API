package com.economato.inventory.application.usecase.smg.model;

import java.time.LocalDateTime;

public record UserIntent(
        String intentType,
        LocalDateTime detectedAt,
        boolean resolved
) {
}
