package com.economato.inventory.exception;

/**
 * Excepción lanzada cuando se detecta un conflicto de concurrencia
 * durante una actualización de datos (Optimistic Locking).
 * 
 * Esta excepción indica que el registro fue modificado por otra transacción
 * entre la lectura inicial y el intento de actualización.
 */
public class ConcurrencyException extends RuntimeException {
    
    public ConcurrencyException(String message) {
        super(message);
    }
    
    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ConcurrencyException(String entityName, Object id) {
        super(String.format("El %s con ID %s fue modificado por otro usuario. Por favor, recarga los datos e intenta nuevamente.", 
            entityName, id));
    }
}
