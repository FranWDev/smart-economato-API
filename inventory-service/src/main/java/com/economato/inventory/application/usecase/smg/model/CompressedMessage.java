package com.economato.inventory.application.usecase.smg.model;

import com.economato.inventory.domain.model.MessageRole;

public record CompressedMessage(
        MessageRole role,
        String content,
        String toolName
) {
}
