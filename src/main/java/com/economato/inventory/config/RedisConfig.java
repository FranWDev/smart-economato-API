package com.economato.inventory.config;

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
         * Configure Lettuce Redis connection factory with aggressive timeouts.
         * This ensures fast failure when Redis is down.
         */
        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
                RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
                redisConfig.setHostName(redisHost);
                redisConfig.setPort(redisPort);

                // Aggressive timeouts: fail fast when Redis is down
                LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                                .commandTimeout(Duration.ofMillis(redisTimeout)) // Command timeout
                                .shutdownTimeout(Duration.ofMillis(100)) // Shutdown timeout
                                .build();

                return new LettuceConnectionFactory(redisConfig, clientConfig);
        }

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
                        @Qualifier("jackson2ObjectMapper") ObjectMapper objectMapper,
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

                cacheConfigurations.put("products_page", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("recipes_page", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigurations.put("product", defaultConfig.entryTtl(Duration.ofHours(3)));
                cacheConfigurations.put("recipe", defaultConfig.entryTtl(Duration.ofHours(4)));
                cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("user", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("userByEmail", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigurations.put("userDetails", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("orders", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("order", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigurations.put("allergens", defaultConfig.entryTtl(Duration.ofHours(48)));
                cacheConfigurations.put("allergen", defaultConfig.entryTtl(Duration.ofHours(48)));
                cacheConfigurations.put("recipeComponents", defaultConfig.entryTtl(Duration.ofHours(6)));
                cacheConfigurations.put("recipeAllergens", defaultConfig.entryTtl(Duration.ofHours(6)));

                RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .enableStatistics()
                                .transactionAware()
                                .build();

                return new CircuitBreakerAwareCacheManager(redisCacheManager, circuitBreakerRegistry);
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
