package com.economato.inventory.application.dto;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;


/**
 * RestPage es una clase DTO que extiende PageImpl de Spring Data.
 * 
 * PROPÓSITO:
 * Facilitar la deserialización de respuestas paginadas desde APIs REST hacia objetos Java.
 * Permite mapear directamente JSON con estructura de página a esta clase de forma automática.
 * 
 * ¿POR QUÉ NO USAR Page NORMAL?
 * - Page es una interfaz de Spring Data optimizada para operaciones internas (BD, repositorios).
 * - Page NO es serializable de manera directa desde JSON con Jackson.
 * - Al recibir JSON de una API REST, Jackson necesita un constructor con @JsonCreator y @JsonProperty
 *   que mapeé cada campo JSON a los parámetros correspondientes.
 * - PageImpl es la implementación concreta, pero tampoco tiene constructor JSON-compatible por defecto.
 * 
 * - @JsonIgnoreProperties: Ignora campos 'pageable' y 'sort' que pueden causar problemas en deserialización.
 * - @JsonCreator: Constructor especial que Jackson usa para crear instancias desde JSON.
 * - @JsonProperty: Mapea cada propiedad JSON a los parámetros del constructor.
 * - Múltiples constructores: Para flexibilidad al crear instancias (con/sin paginación).
 * 
 * Cuando un cliente REST recibe una respuesta paginada de otra API
 * y necesita convertirla automáticamente a un objeto Java tipado y usable.
 */
@JsonIgnoreProperties(ignoreUnknown = true, value = { "pageable", "sort" })
public class RestPage<T> extends PageImpl<T> {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestPage(@JsonProperty("content") List<T> content,
            @JsonProperty("number") int number,
            @JsonProperty("size") int size,
            @JsonProperty("totalElements") Long totalElements,
            @JsonProperty("pageable") JsonNode pageable,
            @JsonProperty("last") boolean last,
            @JsonProperty("totalPages") int totalPages,
            @JsonProperty("sort") JsonNode sort,
            @JsonProperty("first") boolean first,
            @JsonProperty("numberOfElements") int numberOfElements) {
        super(content, size > 0 ? PageRequest.of(number, size) : Pageable.unpaged(), totalElements);
    }

    public RestPage(List<T> content, Pageable pageable, long total) {
        super(content, pageable, total);
    }

    public RestPage(List<T> content) {
        super(content);
    }

    public RestPage() {
        super(new ArrayList<>());
    }
}
