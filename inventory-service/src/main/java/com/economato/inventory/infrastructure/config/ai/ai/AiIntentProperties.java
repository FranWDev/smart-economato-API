package com.economato.inventory.infrastructure.config.ai.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.intents")
public class AiIntentProperties {

    private Map<String, List<String>> patterns = createDefaultPatterns();

    private static Map<String, List<String>> createDefaultPatterns() {
        Map<String, List<String>> defaults = new HashMap<>();
        defaults.put("STOCK_CHECK", List.of("stock", "cu\u00e1nto queda", "hay suficiente", "disponible", "inventario"));
        defaults.put("ORDER_CREATE", List.of("pedir", "pedido", "encargar", "necesitamos", "comprar", "ordenar"));
        defaults.put("MENU_PLAN", List.of("men\u00fa", "plan semanal", "planificar", "semana", "planning"));
        defaults.put("RECIPE_QUERY", List.of("receta", "ingredientes", "c\u00f3mo se hace", "elaboraci\u00f3n", "preparar"));
        defaults.put("COST_ANALYSIS", List.of("coste", "precio", "caro", "barato", "presupuesto", "gasto"));
        defaults.put("ALLERGEN_CHECK", List.of("al\u00e9rgeno", "alergia", "gluten", "lactosa", "cel\u00edaco", "intolerancia"));
        defaults.put("EXPIRY_CHECK", List.of("caducidad", "caduca", "vence", "expiraci\u00f3n", "FEFO", "lote"));
        defaults.put("CRISIS_MGMT", List.of("crisis", "alerta", "problema", "proveedor", "cuarentena", "retirada"));
        defaults.put("TRACEABILITY", List.of("trazabilidad", "origen", "lote", "blockchain", "verificar"));
        return defaults;
    }
}