package com.economato.inventory.application.dto.incident.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@Builder
public class AttachmentDownloadDTO {
    private final Resource resource;
    private final String filename;
    private final String contentType;
}
