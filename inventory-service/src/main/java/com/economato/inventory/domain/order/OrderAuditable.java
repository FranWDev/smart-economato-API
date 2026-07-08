package com.economato.inventory.domain.order;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OrderAuditable {
    String action() default "";
}
