package com.economato.inventory.infrastructure.config.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Value;
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

        @Value("${spring.data.redis.host:localhost}")
        private String redisHost;

        @Value("${spring.data.redis.port:6379}")
        private int redisPort;

        @Value("${spring.data.redis.timeout:500}")
        private long redisTimeout;

        /**
         * Configurar la fábrica de conexiones Lettuce Redis con tiempos de espera agresivos.
         * Esto asegura fallos rápidos cuando Redis no está disponible
         */
        
        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
                RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
                redisConfig.setHostName(redisHost);
                redisConfig.setPort(redisPort);

                LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                                .commandTimeout(Duration.ofMillis(redisTimeout))
                                .shutdownTimeout(Duration.ofMillis(100)) 
                                .build();

                return new LettuceConnectionFactory(redisConfig, clientConfig);
        }

        private static GenericJackson2JsonRedisSerializer buildRedisSerializer(ObjectMapper baseMapper) {
                PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build();

                // copy() para evitar mutar el ObjectMapper global que Spring Boot configura, ya que --
                // activateDefaultTyping es una operación global que afectaría a toda la aplicación y podría causar problemas de seguridad o serialización en otros contextos.
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
        @Primary
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                        @Qualifier("jackson2ObjectMapper") ObjectMapper objectMapper,
                        @Qualifier("l1CaffeineCacheManager") CacheManager l1CaffeineCacheManager,
                        CircuitBreakerRegistry circuitBreakerRegistry) {

                GenericJackson2JsonRedisSerializer serializer = buildRedisSerializer(objectMapper);

                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                                .defaultCacheConfig()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofHours(2));

                Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

                // TIER 1 — Datos maestros
                cacheConfigurations.put("allergens_page", defaultConfig.entryTtl(Duration.ofHours(24)));
                cacheConfigurations.put("allergen", defaultConfig.entryTtl(Duration.ofHours(48)));
                cacheConfigurations.put("suppliers_page", defaultConfig.entryTtl(Duration.ofHours(12)));
                cacheConfigurations.put("supplier", defaultConfig.entryTtl(Duration.ofHours(24)));

                // TIER 2 — Entidades estables
                cacheConfigurations.put("recipe", defaultConfig.entryTtl(Duration.ofHours(4)));
                cacheConfigurations.put("recipes_page", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("user", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("userByEmail", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("users_page", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("users_by_role", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("users_no_teacher", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("order", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("orders_page", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("orders_pending", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("recipe_components_page", defaultConfig.entryTtl(Duration.ofHours(4)));
                cacheConfigurations.put("recipe_component", defaultConfig.entryTtl(Duration.ofHours(6)));
                cacheConfigurations.put("recipe_components_by_recipe", defaultConfig.entryTtl(Duration.ofHours(4)));
                cacheConfigurations.put("weekly_plan", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("weekly_plan_requirements", defaultConfig.entryTtl(Duration.ofMinutes(15)));

                // TIER 3 — Datos volátiles
                cacheConfigurations.put("product", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("products_page", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("products_search", defaultConfig.entryTtl(Duration.ofMinutes(10)));

                // TIER 4 — Datos computados
                cacheConfigurations.put("system_config", defaultConfig.entryTtl(Duration.ofSeconds(30)));
                cacheConfigurations.put("stock_alerts", defaultConfig.entryTtl(Duration.ofMinutes(3)));
                cacheConfigurations.put("stock_predictions", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("daily_forecast", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("weekly_consumption", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("product_stats", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("recipe_stats", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("user_stats", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("order_stats", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("student_metrics", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("kitchen_report", defaultConfig.entryTtl(Duration.ofMinutes(30)));

                // Compatibilidad con nombres legacy aún presentes en algunos flujos
                cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("allergens", defaultConfig.entryTtl(Duration.ofHours(24)));
                cacheConfigurations.put("recipeComponents", defaultConfig.entryTtl(Duration.ofHours(6)));
                cacheConfigurations.put("recipeAllergens", defaultConfig.entryTtl(Duration.ofHours(6)));
                cacheConfigurations.put("userDetails", defaultConfig.entryTtl(Duration.ofMinutes(15)));

                RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .enableStatistics()
                                .transactionAware()
                                .build();

                CacheManager circuitBreakerAwareCacheManager = new CircuitBreakerAwareCacheManager(
                                redisCacheManager,
                                circuitBreakerRegistry);

                return new TwoLevelCacheManager(circuitBreakerAwareCacheManager, l1CaffeineCacheManager);
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
