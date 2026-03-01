package com.economato.inventory.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import org.springframework.beans.factory.annotation.Qualifier;
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

@Configuration
@EnableCaching
@Profile("!test")
public class RedisConfig {

        private static GenericJackson2JsonRedisSerializer buildRedisSerializer(ObjectMapper baseMapper) {
                PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build();

                // copy() produces an independent instance: the application-wide singleton
                // is never mutated by activateDefaultTyping.
                ObjectMapper redisMapper = baseMapper.copy();
                redisMapper.activateDefaultTyping(
                                ptv,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);

                return new GenericJackson2JsonRedisSerializer(redisMapper);
        }

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
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                        @Qualifier("jackson2ObjectMapper") ObjectMapper objectMapper) {

                GenericJackson2JsonRedisSerializer serializer = buildRedisSerializer(objectMapper);

                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                                .defaultCacheConfig()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(30));

                Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

                cacheConfigurations.put("products_page_v4", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("recipes_page_v4", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("product_v4", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("recipe_v4", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("user", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("userByEmail", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("userDetails", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("order", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("allergens", defaultConfig.entryTtl(Duration.ofHours(24)));
                cacheConfigurations.put("allergen", defaultConfig.entryTtl(Duration.ofHours(24)));
                cacheConfigurations.put("recipeComponents", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("recipeAllergens", defaultConfig.entryTtl(Duration.ofHours(2)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .transactionAware()
                                .build();
        }

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                        @Qualifier("jackson2ObjectMapper") ObjectMapper objectMapper) {

                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);

                template.setKeySerializer(new StringRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());

                GenericJackson2JsonRedisSerializer serializer = buildRedisSerializer(objectMapper);

                template.setValueSerializer(serializer);
                template.setHashValueSerializer(serializer);

                template.afterPropertiesSet();
                return template;
        }
}
