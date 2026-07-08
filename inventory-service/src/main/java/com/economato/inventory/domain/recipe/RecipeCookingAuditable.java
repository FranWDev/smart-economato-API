package com.economato.inventory.domain.recipe;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RecipeCookingAuditable {
    String action() default "COOK_RECIPE";
}
