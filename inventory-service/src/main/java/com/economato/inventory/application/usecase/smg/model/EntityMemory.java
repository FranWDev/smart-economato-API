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
            for (ProductSnapshot p : products.values()) {
                sb.append("P").append(p.id())
                        .append(":")
                        .append(safe(p.name())).append("|")
                        .append(safe(p.stock())).append(safeUnit(p.unit())).append("|")
                        .append("price:").append(safe(p.price())).append("|")
                        .append("alert:").append(safe(p.alertLevel())).append("|")
                        .append("pred14d:").append(safe(p.prediction14d())).append("|")
                        .append("expDays:").append(safe(p.daysToExpiry()))
                        .append("\n");
            }
        }

        if (!recipes.isEmpty()) {
            for (RecipeSnapshot r : recipes.values()) {
                sb.append("R").append(r.id())
                        .append(":")
                        .append(safe(r.name())).append("|")
                        .append("cost:").append(safe(r.cost())).append("|")
                        .append("allergens:").append(r.allergens() == null ? "-" : String.join(",", r.allergens()))
                        .append("\n");
            }
        }

        if (!orders.isEmpty()) {
            for (OrderSnapshot o : orders.values()) {
                sb.append("O").append(o.id())
                        .append(":")
                        .append(safe(o.status())).append("|")
                        .append("supplier:").append(safe(o.supplierName())).append("|")
                        .append("items:").append(o.itemCount()).append("|")
                        .append("total:").append(safe(o.total()))
                        .append("\n");
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
