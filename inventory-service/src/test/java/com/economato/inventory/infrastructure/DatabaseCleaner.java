package com.economato.inventory.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DatabaseCleaner {

    private final EntityManager entityManager;
    private volatile Set<String> cachedTableNames;

    public DatabaseCleaner(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void clear() {
        if (cachedTableNames == null) {
            cachedTableNames = entityManager.getMetamodel().getEntities().stream()
                    .map(entity -> {
                        Table tableAnnotation = entity.getJavaType().getAnnotation(Table.class);
                        return tableAnnotation != null ? tableAnnotation.name() : entity.getName().toLowerCase();
                    })
                    .collect(Collectors.toSet());
        }

        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        for (String tableName : cachedTableNames) {
            try {
                entityManager.createNativeQuery("TRUNCATE TABLE " + tableName + " RESTART IDENTITY").executeUpdate();
            } catch (Exception e) {

                System.err.println("No se pudo truncar la tabla: " + tableName + " - " + e.getMessage());
            }
        }

        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
