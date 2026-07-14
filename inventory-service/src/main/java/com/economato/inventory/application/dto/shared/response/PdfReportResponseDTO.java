package com.economato.inventory.application.dto.shared.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PdfReportResponseDTO {
    private final byte[] bytes;
    private final String filename;
}
