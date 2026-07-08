package com.economato.inventory.application.usecase.smg.model.user;

import java.time.LocalDateTime;

public record UserIntent(
        String intentType,
        LocalDateTime detectedAt,
        boolean resolved
) {
}
