package com.economato.inventory.application.usecase.smg.model.shared;

import com.economato.inventory.domain.model.user.MessageRole;

public record CompressedMessage(
        MessageRole role,
        String content,
        String toolName
) {
}
