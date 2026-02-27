package com.economato.inventory.config;

public class DbContextHolder {

    public static final ScopedValue<DataSourceType> CONTEXT = ScopedValue.newInstance();

    public static DataSourceType get() {
        return CONTEXT.isBound() ? CONTEXT.get() : DataSourceType.WRITER;
    }
}
