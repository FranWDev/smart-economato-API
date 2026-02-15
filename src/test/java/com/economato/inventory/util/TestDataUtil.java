package com.economato.inventory.util;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.economato.inventory.dto.request.*;
import com.economato.inventory.model.*;

public class TestDataUtil {

    private static PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public static void setPasswordEncoder(PasswordEncoder encoder) {
        if (encoder != null) {
            passwordEncoder = encoder;
        }
    }

    public static User createAdminUser() {
        User user = new User();
        user.setName("Admin");
        user.setUser("adminUser");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setRole(Role.ADMIN);
        user.setOrders(new ArrayList<>());
        user.setInventoryMovements(new ArrayList<>());
        return user;
    }

    public static User createChefUser() {
        User user = new User();
        user.setName("Chef");
        user.setUser("chefUser");
        user.setPassword(passwordEncoder.encode("chef123"));
        user.setRole(Role.CHEF);
        user.setOrders(new ArrayList<>());
        user.setInventoryMovements(new ArrayList<>());
        return user;
    }

    public static User createRegularUser() {
        User user = new User();
        user.setName("User");
        user.setUser("regularUser");
        user.setPassword(passwordEncoder.encode("user123"));
        user.setRole(Role.USER);
        user.setOrders(new ArrayList<>());
        user.setInventoryMovements(new ArrayList<>());
        return user;
    }

    public static Product createProduct(String name, String type, String unit, BigDecimal price, String code,
            BigDecimal stock) {
        Product product = new Product();
        product.setName(name);
        product.setType(type);
        product.setUnit(unit);
        product.setUnitPrice(price);
        product.setProductCode(code);
        // Ajustar escala del stock para compatibilidad con la base de datos
        // (precision=10, scale=3)
        product.setCurrentStock(stock.setScale(3, java.math.RoundingMode.HALF_UP));
        product.setMinimumStock(BigDecimal.ZERO); // Default to 0 for tests
        product.setOrderDetails(new ArrayList<>());
        return product;
    }

    public static Product createFlour() {
        return createProduct("Harina", "Ingrediente", "KG", new BigDecimal("2.50"), "HAR001", new BigDecimal("100.0"));
    }

    public static Product createSugar() {
        return createProduct("Azúcar", "Ingrediente", "KG", new BigDecimal("1.80"), "AZU001", new BigDecimal("50.0"));
    }

    public static Product createEggs() {
        return createProduct("Huevos", "Ingrediente", "UND", new BigDecimal("0.20"), "HUE001", new BigDecimal("200.0"));
    }

    public static Allergen createAllergen(String name) {
        Allergen allergen = new Allergen();
        allergen.setName(name);
        allergen.setRecipes(new ArrayList<>());
        return allergen;
    }

    public static Allergen createGlutenAllergen() {
        return createAllergen("Gluten");
    }

    public static Allergen createEggAllergen() {
        return createAllergen("Huevos");
    }

    public static Allergen createNutsAllergen() {
        return createAllergen("Frutos secos");
    }

    public static Recipe createRecipe(String name, String elaboration, String presentation, BigDecimal totalCost) {
        Recipe recipe = new Recipe();
        recipe.setName(name);
        recipe.setElaboration(elaboration);
        recipe.setPresentation(presentation);
        recipe.setTotalCost(totalCost);
        recipe.setComponents(new ArrayList<>());
        recipe.setAllergens(new HashSet<>());
        return recipe;
    }

    public static Recipe createBasicCakeRecipe() {
        Recipe recipe = createRecipe(
                "Pastel básico",
                "1. Mezclar ingredientes secos\n2. Agregar huevos\n3. Hornear a 180°C",
                "Decorar con azúcar glas",
                new BigDecimal("10.00"));
        recipe.setAllergens(new HashSet<>(Arrays.asList(createGlutenAllergen(), createEggAllergen())));
        return recipe;
    }

    public static RecipeComponent createRecipeComponent(Recipe recipe, Product product, BigDecimal quantity) {
        RecipeComponent component = new RecipeComponent();
        component.setParentRecipe(recipe);
        component.setProduct(product);
        component.setQuantity(quantity);
        return component;
    }

    public static Order createOrder(User user, String status) {
        Order order = new Order();
        order.setUsers(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(status);
        order.setDetails(new ArrayList<>());
        return order;
    }

    public static OrderDetail createOrderDetail(Order order, Product product, BigDecimal quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setOrder(order);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        order.getDetails().add(detail);
        return detail;
    }

    public static InventoryAudit createInventoryAudit(User user, Product product, String movementType,
            BigDecimal quantity) {
        InventoryAudit audit = new InventoryAudit();
        audit.setUsers(user);
        audit.setProduct(product);
        audit.setMovementDate(LocalDateTime.now());
        audit.setQuantity(quantity);
        audit.setMovementType(movementType);
        audit.setActionDescription("Test movement: " + movementType);
        return audit;
    }

    public static RecipeAudit createRecipeAudit(Recipe recipe, String action, String details) {
        RecipeAudit audit = new RecipeAudit();
        audit.setRecipe(recipe);
        audit.setAuditDate(LocalDateTime.now());
        audit.setAction(action);
        audit.setDetails(details);
        return audit;
    }

    public static Recipe createCompleteRecipe() {
        Recipe recipe = createBasicCakeRecipe();
        Product flour = createFlour();
        Product sugar = createSugar();

        recipe.setComponents(Arrays.asList(
                createRecipeComponent(recipe, flour, new BigDecimal("0.5")),
                createRecipeComponent(recipe, sugar, new BigDecimal("0.3"))));

        return recipe;
    }

    public static Order createCompleteOrder(User user) {
        Order order = createOrder(user, "PENDING");

        Product flour = createFlour();
        Product sugar = createSugar();

        createOrderDetail(order, flour, new BigDecimal("2.0"));
        createOrderDetail(order, sugar, new BigDecimal("1.5"));

        return order;
    }

    public static ProductRequestDTO createProductRequestDTO() {
        ProductRequestDTO dto = new ProductRequestDTO();
        dto.setName("Harina de trigo");
        dto.setType("Ingrediente");
        dto.setUnit("KG");
        dto.setUnitPrice(new BigDecimal("2.50"));
        dto.setProductCode("HAR002");
        dto.setCurrentStock(new BigDecimal("100.0"));
        dto.setMinimumStock(BigDecimal.ZERO); // Default for tests
        return dto;
    }

    public static UserRequestDTO createUserRequestDTO() {
        UserRequestDTO dto = new UserRequestDTO();
        dto.setName("UsuarioTest");
        dto.setUser("testUser");
        dto.setPassword("password123");
        dto.setRole(Role.USER);
        return dto;
    }
}
