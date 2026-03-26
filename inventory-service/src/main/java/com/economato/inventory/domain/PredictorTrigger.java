package com.economato.inventory.domain;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PredictorTrigger {
    String action();
}
