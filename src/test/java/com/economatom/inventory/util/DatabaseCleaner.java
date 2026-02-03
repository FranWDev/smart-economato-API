package com.economatom.inventory.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DatabaseCleaner {

    private final EntityManager entityManager;

    public DatabaseCleaner(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void clear() {
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        Set<EntityType<?>> entities = entityManager.getMetamodel().getEntities();
        Set<String> tableNames = entities.stream()
                .map(entity -> {
                    Table tableAnnotation = entity.getJavaType().getAnnotation(Table.class);
                    return tableAnnotation != null ? tableAnnotation.name() : entity.getName().toLowerCase();
                })
                .collect(Collectors.toSet());

        for (String tableName : tableNames) {
            try {
                entityManager.createNativeQuery("TRUNCATE TABLE " + tableName + " RESTART IDENTITY").executeUpdate();
            } catch (Exception e) {

                System.err.println("No se pudo truncar la tabla: " + tableName + " - " + e.getMessage());
            }
        }

        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
