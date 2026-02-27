package com.economato.inventory.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Redis para caché con invalidación eventual.
 * 
 * Características:
 * - Serialización JSON para objetos complejos
 * - TTL personalizado por tipo de caché
 * - Invalidación manual mediante @CacheEvict
 */
@Configuration
@EnableCaching
@Profile("!test")
public class RedisConfig {

        /**
         * Configuración del CacheManager con TTL personalizados.
         * 
         * Caches configurados:
         * - products: 1 hora (datos que cambian con frecuencia)
         * - recipes: 2 horas (datos más estables)
         * - users: 30 minutos (datos de autenticación)
         * - orders: 15 minutos (datos transaccionales)
         * - allergens: 24 horas (datos maestros)
         */
        @Bean
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                // ObjectMapper personalizado para serialización
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.activateDefaultTyping(
                                LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);

                // Configuración de serialización
                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

                // Configuración base de Redis Cache
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                                .defaultCacheConfig()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(30)); // TTL por defecto

                // Configuraciones específicas por tipo de caché
                Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

                // Products and Recipes Pages (v4)
                cacheConfigurations.put("products_page_v4", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("recipes_page_v4", defaultConfig.entryTtl(Duration.ofMinutes(10)));

                // Products: 1 hora (se modifican con frecuencia)
                cacheConfigurations.put("product_v4", defaultConfig.entryTtl(Duration.ofHours(1)));

                // Recipes: 2 horas (más estables)
                cacheConfigurations.put("recipe_v4", defaultConfig.entryTtl(Duration.ofHours(2)));

                // Users: 30 minutos (datos de autenticación/autorización)
                cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("user", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("userByEmail", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("userDetails", defaultConfig.entryTtl(Duration.ofMinutes(15)));

                // Orders: 15 minutos (datos transaccionales)
                cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("order", defaultConfig.entryTtl(Duration.ofMinutes(15)));

                // Allergens: 24 horas (datos maestros que raramente cambian)
                cacheConfigurations.put("allergens", defaultConfig.entryTtl(Duration.ofHours(24)));
                cacheConfigurations.put("allergen", defaultConfig.entryTtl(Duration.ofHours(24)));

                // Recipe components y allergens: 2 horas
                cacheConfigurations.put("recipeComponents", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("recipeAllergens", defaultConfig.entryTtl(Duration.ofHours(2)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .transactionAware() // Soporte para transacciones
                                .build();
        }

        /**
         * RedisTemplate para operaciones manuales con Redis si son necesarias.
         */
        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);

                // Serialización de claves como String
                template.setKeySerializer(new StringRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());

                // Serialización de valores como JSON
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                objectMapper.activateDefaultTyping(
                                LaissezFaireSubTypeValidator.instance,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);

                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

                template.setValueSerializer(serializer);
                template.setHashValueSerializer(serializer);

                template.afterPropertiesSet();
                return template;
        }
}
