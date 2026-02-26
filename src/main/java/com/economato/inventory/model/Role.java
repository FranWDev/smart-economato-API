package com.economato.inventory.model;

public enum Role {
    /**
     * Usuario con permisos de administración completos
     */
    ADMIN,

    /**
     * Usuario chef con permisos para gestionar recetas y cocina
     */
    CHEF,

    /**
     * Usuario estándar con permisos temporalmente escalados (mismos permisos que
     * CHEF)
     */
    ELEVATED,

    /**
     * Usuario estándar con permisos básicos
     */
    USER
}
