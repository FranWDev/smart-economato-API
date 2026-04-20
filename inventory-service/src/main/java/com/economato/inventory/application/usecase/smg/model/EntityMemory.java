package com.economato.inventory.application.usecase.smg.model;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Getter
public class EntityMemory {

    private final Set<Integer> productIds = new LinkedHashSet<>();
    private final Set<String> productNames = new LinkedHashSet<>();
    private final Set<Integer> recipeIds = new LinkedHashSet<>();
    private final Set<String> recipeNames = new LinkedHashSet<>();
    private final Set<Integer> orderIds = new LinkedHashSet<>();

    private final Map<Integer, ProductSnapshot> products = new LinkedHashMap<>();
    private final Map<Integer, RecipeSnapshot> recipes = new LinkedHashMap<>();
    private final Map<Integer, OrderSnapshot> orders = new LinkedHashMap<>();

    public void addProductById(Integer id) {
        if (id != null) {
            productIds.add(id);
        }
    }

    public void addProductByName(String name) {
        if (name != null && !name.isBlank()) {
            productNames.add(name);
        }
    }

    public void addRecipeById(Integer id) {
        if (id != null) {
            recipeIds.add(id);
        }
    }

    public void addRecipeByName(String name) {
        if (name != null && !name.isBlank()) {
            recipeNames.add(name);
        }
    }

    public void addOrderById(Integer id) {
        if (id != null) {
            orderIds.add(id);
        }
    }

    public void putProductSnapshot(ProductSnapshot snapshot) {
        if (snapshot != null && snapshot.id() != null) {
            products.put(snapshot.id(), snapshot);
            addProductById(snapshot.id());
            addProductByName(snapshot.name());
        }
    }

    public void putRecipeSnapshot(RecipeSnapshot snapshot) {
        if (snapshot != null && snapshot.id() != null) {
            recipes.put(snapshot.id(), snapshot);
            addRecipeById(snapshot.id());
            addRecipeByName(snapshot.name());
        }
    }

    public void putOrderSnapshot(OrderSnapshot snapshot) {
        if (snapshot != null && snapshot.id() != null) {
            orders.put(snapshot.id(), snapshot);
            addOrderById(snapshot.id());
        }
    }

    public int totalEntityCount() {
        return productIds.size() + recipeIds.size() + orderIds.size();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();

        if (!products.isEmpty()) {
            sb.append("## Products\n");
            for (ProductSnapshot p : products.values()) {
                sb.append("- ").append(safe(p.name())).append(" (ID:").append(p.id()).append("): ");
                sb.append(safe(p.stock())).append(" ").append(safeUnit(p.unit()));
                sb.append(", price: ").append(safe(p.price()));
                if (p.alertLevel() != null && !"-".equals(safe(p.alertLevel()))) {
                    sb.append(" [ALERT: ").append(p.alertLevel()).append("]");
                }
                if (p.prediction14d() != null && !"-".equals(safe(p.prediction14d()))) {
                    sb.append(", 14d forecast: ").append(p.prediction14d());
                }
                if (p.daysToExpiry() != null && !"-".equals(safe(p.daysToExpiry()))) {
                    sb.append(", expires in ").append(p.daysToExpiry()).append("d");
                }
                sb.append("\n");
            }
        }

        if (!recipes.isEmpty()) {
            sb.append("## Recipes\n");
            for (RecipeSnapshot r : recipes.values()) {
                sb.append("- ").append(safe(r.name())).append(" (ID:").append(r.id()).append("): ");
                sb.append("cost ").append(safe(r.cost()));
                if (r.allergens() != null && !r.allergens().isEmpty()) {
                    sb.append(", allergens: ").append(String.join(", ", r.allergens()));
                }
                sb.append("\n");
            }
        }

        if (!orders.isEmpty()) {
            sb.append("## Orders\n");
            for (OrderSnapshot o : orders.values()) {
                sb.append("- Order #").append(o.id()).append(": ");
                sb.append(safe(o.status()));
                sb.append(", supplier: ").append(safe(o.supplierName()));
                sb.append(", ").append(o.itemCount()).append(" items");
                sb.append(", total: ").append(safe(o.total()));
                sb.append("\n");
            }
        }

        if (sb.length() == 0) {
            if (!productIds.isEmpty() || !recipeIds.isEmpty() || !orderIds.isEmpty()) {
                sb.append("products=").append(productIds)
                        .append(", recipes=").append(recipeIds)
                        .append(", orders=").append(orderIds);
            } else if (!productNames.isEmpty() || !recipeNames.isEmpty()) {
                sb.append("productNames=").append(productNames)
                        .append(", recipeNames=").append(recipeNames);
            } else {
                sb.append("no-entities");
            }
        }

        return sb.toString().trim();
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String safeUnit(String value) {
        return value == null ? "" : value;
    }
}
