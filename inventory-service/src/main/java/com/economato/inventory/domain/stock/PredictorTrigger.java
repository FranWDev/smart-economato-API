package com.economato.inventory.domain.stock;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PredictorTrigger {
    String action();
}
