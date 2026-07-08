package com.economato.inventory.infrastructure.shared;
import com.economato.inventory.application.dto.product.request.ProductRequestDTO;
import com.economato.inventory.application.dto.user.request.UserRequestDTO;
import com.economato.inventory.application.dto.weeklyplan.request.WeeklyPlanRequestDTO;
import com.economato.inventory.application.dto.weeklyplan.request.WeeklyPlanSlotRequestDTO;
import com.economato.inventory.domain.model.ai.AiChat;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.ai.AiChatStatus;
import com.economato.inventory.domain.model.ai.AiProvider;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderDetail;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.domain.model.product.Product;
import com.economato.inventory.domain.model.recipe.Allergen;
import com.economato.inventory.domain.model.recipe.Recipe;
import com.economato.inventory.domain.model.recipe.RecipeAudit;
import com.economato.inventory.domain.model.recipe.RecipeComponent;
import com.economato.inventory.domain.model.shared.InventoryAudit;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.domain.model.user.Role;
import com.economato.inventory.domain.model.user.User;
import com.economato.inventory.domain.model.user.UserApiKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;




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

    public static User createUser(String name, String username, String plainPassword, Role role) {
        User user = new User();
        user.setName(name);
        user.setUser(username);
        user.setPassword(passwordEncoder.encode(plainPassword));
        user.setRole(role);
        user.setOrders(new ArrayList<>());
        user.setInventoryMovements(new ArrayList<>());
        return user;
    }

    public static Product createProduct(String name, String unit, BigDecimal price, String code,
            BigDecimal stock) {
        Product product = new Product();
        product.setName(name);
        product.setUnit(unit);
        product.setUnitPrice(price);
        product.setProductCode(code);
        product.setCurrentStock(stock.setScale(3, java.math.RoundingMode.HALF_UP));
        product.setOrderDetails(new ArrayList<>());
        return product;
    }

    public static Product createFlour() {
        return createProduct("Harina", "KG", new BigDecimal("2.50"), "HAR001", new BigDecimal("100.0"));
    }

    public static Product createSugar() {
        return createProduct("Azúcar", "KG", new BigDecimal("1.80"), "AZU001", new BigDecimal("50.0"));
    }

    public static Product createEggs() {
        return createProduct("Huevos", "UND", new BigDecimal("0.20"), "HUE001", new BigDecimal("200.0"));
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
        recipe.setPortions(BigDecimal.ONE); // Importante: Valor por defecto para evitar NPE
        recipe.setComponents(new HashSet<>());
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

    public static Order createOrder(User user, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
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
        audit.setUser(user);
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

        recipe.setComponents(new HashSet<>(Arrays.asList(
                createRecipeComponent(recipe, flour, new BigDecimal("0.5")),
                createRecipeComponent(recipe, sugar, new BigDecimal("0.3")))));

        return recipe;
    }

    public static Order createCompleteOrder(User user) {
        Order order = createOrder(user, OrderStatus.PENDING);

        Product flour = createFlour();
        Product sugar = createSugar();

        createOrderDetail(order, flour, new BigDecimal("2.0"));
        createOrderDetail(order, sugar, new BigDecimal("1.5"));

        return order;
    }

    public static ProductRequestDTO createProductRequestDTO() {
        ProductRequestDTO dto = new ProductRequestDTO();
        dto.setName("Harina de trigo");
        dto.setUnit("KG");
        dto.setUnitPrice(new BigDecimal("2.50"));
        dto.setProductCode("HAR002");
        dto.setCurrentStock(new BigDecimal("100.0"));
        dto.setExpirationDate(java.time.LocalDate.now().plusDays(30));
        dto.setLotQuantity(new BigDecimal("1.000"));
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

    public static WeeklyPlanSlotRequestDTO createWeeklyPlanSlotRequestDTO(Integer recipeId, java.math.BigDecimal quantity, Integer dayOfWeek, java.time.LocalTime startTime, java.time.LocalTime endTime, Integer sortOrder, java.util.List<Integer> studentIds) {
        WeeklyPlanSlotRequestDTO dto = new WeeklyPlanSlotRequestDTO();
        dto.setRecipeId(recipeId);
        dto.setQuantity(quantity);
        dto.setDayOfWeek(dayOfWeek);
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        dto.setSortOrder(sortOrder);
        dto.setStudentIds(studentIds);
        return dto;
    }

    public static WeeklyPlanRequestDTO createWeeklyPlanRequestDTO(Integer chefId, java.time.LocalDate weekStartDate, java.util.List<WeeklyPlanSlotRequestDTO> slots) {
        WeeklyPlanRequestDTO dto = new WeeklyPlanRequestDTO();
        dto.setChefId(chefId);
        dto.setWeekStartDate(weekStartDate);
        dto.setSlots(slots);
        return dto;
    }

    public static AiChat createAiChat(User user, AiProvider provider) {
        return createAiChat(user, provider, AiChatStatus.ACTIVE);
    }

    public static AiChat createAiChat(User user, AiProvider provider, AiChatStatus status) {
        AiChat chat = new AiChat();
        chat.setUser(user);
        chat.setTitle("Chat de prueba AI");
        chat.setStatus(status);
        chat.setActiveProvider(provider);
        chat.setUserLanguage("es");
        chat.setCreatedAt(LocalDateTime.now());
        chat.setLastMessageAt(LocalDateTime.now());
        chat.setMessageCount(0);
        chat.setTotalTokensConsumed(0L);
        return chat;
    }

    public static AiChatMessage createAiChatMessage(AiChat chat, MessageRole role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setChat(chat);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    public static AiChatMessage createAiChatToolMessage(AiChat chat, String toolName, String toolResult) {
        AiChatMessage message = createAiChatMessage(chat, MessageRole.TOOL, "Tool invocation");
        message.setToolName(toolName);
        message.setToolResult(toolResult);
        return message;
    }

    public static UserApiKey createUserApiKey(User user, AiProvider provider, String encryptedKey, String hint) {
        UserApiKey key = new UserApiKey();
        key.setUser(user);
        key.setProvider(provider);
        key.setEncryptedKey(encryptedKey);
        key.setKeyHint(hint);
        key.setEncryptionKeyVersion(1);
        key.setActive(true);
        key.setCreatedAt(LocalDateTime.now());
        return key;
    }

    public static List<AiChatMessage> createChatHistory(AiChat chat, int messageCount) {
        List<AiChatMessage> history = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            if (i % 2 == 0) {
                history.add(createAiChatMessage(chat, MessageRole.USER,
                        "¿Cuánto stock queda de harina para esta semana? (" + i + ")"));
            } else {
                history.add(createAiChatMessage(chat, MessageRole.ASSISTANT,
                        "Actualmente hay 45.5 KG de harina y el consumo proyectado es estable. (" + i + ")"));
            }
        }
        return history;
    }

    public static List<AiChatMessage> createChatHistoryWithToolCalls(AiChat chat) {
        List<AiChatMessage> history = new ArrayList<>();
        history.add(createAiChatMessage(chat, MessageRole.USER, "Necesito revisar stock y hacer un pedido"));
        history.add(createAiChatToolMessage(chat, "get-product-deep", "{\"productId\":42,\"name\":\"Harina\"}"));
        history.add(createAiChatMessage(chat, MessageRole.ASSISTANT, "He analizado el stock de harina"));
        history.add(createAiChatToolMessage(chat, "create-order", "{\"orderId\":1001,\"status\":\"PENDING\"}"));
        history.add(createAiChatMessage(chat, MessageRole.ASSISTANT, "Pedido generado correctamente"));
        return history;
    }
}
