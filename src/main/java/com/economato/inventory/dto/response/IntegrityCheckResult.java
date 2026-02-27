package com.economato.inventory.dto.response;

import java.util.List;

public class IntegrityCheckResult {
    private final Integer productId;
    private final String productName;
    private final boolean valid;
    private final String message;
    private final List<String> errors;

    public IntegrityCheckResult(Integer productId, String productName, boolean valid, String message,
            List<String> errors) {
        this.productId = productId;
        this.productName = productName;
        this.valid = valid;
        this.message = message;
        this.errors = errors;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getErrors() {
        return errors;
    }
}
