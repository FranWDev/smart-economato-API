package com.economato.inventory.application.usecase.smg.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMemoryTest {

    @Test
    void serialize_emptyMemoryFallsBackToNoEntities() {
        assertEquals("no-entities", new EntityMemory().serialize());
    }

    @Test
    void serialize_formatsReadableSections() {
        EntityMemory memory = new EntityMemory();
        memory.putProductSnapshot(new ProductSnapshot(
                42,
                "Harina",
                new BigDecimal("5"),
                "KG",
                new BigDecimal("2.50"),
                "HIGH",
                new BigDecimal("8.00"),
                3
        ));
        memory.putRecipeSnapshot(new RecipeSnapshot(
                7,
                "Pan",
                new BigDecimal("1.20"),
                List.of("gluten", "milk"),
                Map.of(42, new BigDecimal("0.500"))
        ));
        memory.putOrderSnapshot(new OrderSnapshot(
                9,
                "CONFIRMED",
                "Proveedor X",
                4,
                new BigDecimal("12.34")
        ));

        String serialized = memory.serialize();

        assertTrue(serialized.contains("## Products"));
        assertTrue(serialized.contains("- Harina (ID:42): 5 KG, price: 2.50 [ALERT: HIGH], 14d forecast: 8.00, expires in 3d"));
        assertTrue(serialized.contains("## Recipes"));
        assertTrue(serialized.contains("- Pan (ID:7): cost 1.20, allergens: gluten, milk"));
        assertTrue(serialized.contains("## Orders"));
        assertTrue(serialized.contains("- Order #9: CONFIRMED, supplier: Proveedor X, 4 items, total: 12.34"));
    }
}
