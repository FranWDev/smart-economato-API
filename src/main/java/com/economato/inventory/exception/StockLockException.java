package com.economato.inventory.exception;

/**
 * Excepción lanzada cuando no se puede obtener un bloqueo
 * sobre el stock de un producto debido a concurrencia.
 */
public class StockLockException extends RuntimeException {
    
    public StockLockException(String message) {
        super(message);
    }
    
    public StockLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
