package com.economato.inventory.i18n;

public enum MessageKey {
    // Definición de las claves para I18n relacionadas con Reglas de Negocio /
    // Excepciones.
    // Esto se actualizará progresivamente con el script de refactoring.
    ERROR_USER_DELETE_LAST_ADMIN("error.user.delete.last.admin"),
    ERROR_USER_REQUIRE_CURRENT_PASSWORD("error.user.require.current.password"),
    ERROR_USER_INVALID_CURRENT_PASSWORD("error.user.invalid.current.password"),
    ERROR_USER_HIDE_LAST_ADMIN("error.user.hide.last.admin"),
    ERROR_USER_ADMIN_CANNOT_HAVE_TEACHER("error.user.admin.cannot.have.teacher"),
    ERROR_USER_TEACHER_MUST_BE_ADMIN("error.user.teacher.must.be.admin"),
    ERROR_USER_ONLY_ADMIN_HAS_STUDENTS("error.user.only.admin.has.students"),
    ERROR_USER_ALREADY_ELEVATED("error.user.already.elevated"),
    ERROR_USER_CANNOT_ESCALATE_ADMIN("error.user.cannot.escalate.admin"),
    ERROR_AUTH_USER_ALREADY_EXISTS("error.auth.user.already.exists"),
    ERROR_AUTH_INVALID_LOGOUT_TOKEN("error.auth.invalid.logout.token"),
    ERROR_AUTH_LOGOUT_TOKEN_REQUIRED("error.auth.logout.token.required"),
    ERROR_AUTH_UNAUTHORIZED("error.auth.unauthorized"),

    ERROR_PRODUCT_ALREADY_EXISTS("error.product.already.exists"),
    ERROR_PRODUCT_DELETE_HAS_MOVEMENTS("error.product.delete.has.movements"),
    ERROR_PRODUCT_DELETE_IN_RECIPE("error.product.delete.in.recipe"),
    ERROR_PRODUCT_INVALID_UNIT("error.product.invalid.unit"),
    ERROR_PRODUCT_SUPPLIER_NOT_FOUND("error.product.supplier.not.found"),

    ERROR_SUPPLIER_ALREADY_EXISTS("error.supplier.already.exists"),

    ERROR_RECIPE_NO_COMPONENTS("error.recipe.no.components"),
    ERROR_RECIPE_ID_NOT_PROVIDED("error.recipe.id.not.provided"),
    ERROR_RECIPE_ID_NULL("error.recipe.id.null"),

    ERROR_ORDER_NOT_FOUND("error.order.not.found"),
    ERROR_ORDER_PRODUCT_NOT_FOUND("error.order.product.not.found"),
    ERROR_ORDER_CANNOT_RECEIVE_LESS("error.order.cannot.receive.less"),
    ERROR_ORDER_INVALID_STATE("error.order.invalid.state"),

    ERROR_OPTIMISTIC_LOCK("error.optimistic.lock"),
    ERROR_PESSIMISTIC_LOCK("error.pessimistic.lock"),
    ERROR_INTERNAL_SERVER_ERROR("error.internal.server.error"),
    ERROR_AUTH_BAD_CREDENTIALS("error.auth.bad.credentials"),
    ERROR_AUTH_JWT_INVALID("error.auth.jwt.invalid"),
    ERROR_AUTH_JWT_MISSING("error.auth.jwt.missing"),

    ERROR_RESOURCE_NOT_FOUND("error.resource.not.found"),
    ERROR_USER_NOT_FOUND("error.user.not.found"),
    ERROR_PRODUCT_NOT_FOUND("error.product.not.found"),
    ERROR_RECIPE_NOT_FOUND("error.recipe.not.found"),
    ERROR_SUPPLIER_NOT_FOUND("error.supplier.not.found"),
    ERROR_SUPPLIER_DELETE_HAS_PRODUCTS("error.supplier.delete.has.products"),
    ERROR_ORDER_NOT_FOUND_GENERAL("error.order.not.found.general"),
    ERROR_RECIPE_STOCK_INSUFFICIENT("error.recipe.stock.insufficient"),
    ERROR_STOCK_HASH_CALCULATION("error.stock.hash.calculation"),
    ERROR_AUTH_USER_HIDDEN("error.auth.user.hidden");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
