package com.economato.inventory.application.dto.ledger.response;

import java.util.List;

public class LedgerPdfResponseDTO {
    private final byte[] pdfContent;
    private final boolean integrityValid;
    private final String integrityMessage;
    private final List<String> integrityErrors;

    public LedgerPdfResponseDTO(byte[] pdfContent, boolean integrityValid, 
                                 String integrityMessage, List<String> integrityErrors) {
        this.pdfContent = pdfContent;
        this.integrityValid = integrityValid;
        this.integrityMessage = integrityMessage;
        this.integrityErrors = integrityErrors;
    }

    public byte[] getPdfContent() {
        return pdfContent;
    }

    public boolean isIntegrityValid() {
        return integrityValid;
    }

    public String getIntegrityMessage() {
        return integrityMessage;
    }

    public List<String> getIntegrityErrors() {
        return integrityErrors;
    }
}
