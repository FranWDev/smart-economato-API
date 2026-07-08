package com.economato.inventory.domain.product;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProductAuditable {
    String action() default "";
}
