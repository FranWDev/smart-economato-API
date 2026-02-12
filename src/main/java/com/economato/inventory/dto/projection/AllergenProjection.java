package com.economato.inventory.dto.projection;

/**
 * Proyección de interfaz para Allergen.
 * Spring Data JPA solo selecciona las columnas correspondientes a los getters
 * definidos.
 */
public interface AllergenProjection {

    Integer getId();

    String getName();
}
