package com.economato.inventory.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig {

        @Bean
        public RedisCacheConfiguration cacheConfiguration() {
                return RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(30)) // Default TTL 30 mins
                                .disableCachingNullValues()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        }

        @Bean
        public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
                return (builder) -> builder
                                .withCacheConfiguration("recipes_page_v2",
                                                RedisCacheConfiguration.defaultCacheConfig()
                                                                .entryTtl(Duration.ofMinutes(10))
                                                                .serializeKeysWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new StringRedisSerializer()))
                                                                .serializeValuesWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer())))
                                .withCacheConfiguration("products_page_v2",
                                                RedisCacheConfiguration.defaultCacheConfig()
                                                                .entryTtl(Duration.ofMinutes(10))
                                                                .serializeKeysWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new StringRedisSerializer()))
                                                                .serializeValuesWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer())))
                                .withCacheConfiguration("recipe_v2",
                                                RedisCacheConfiguration.defaultCacheConfig()
                                                                .entryTtl(Duration.ofHours(1))
                                                                .serializeKeysWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new StringRedisSerializer()))
                                                                .serializeValuesWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer())))
                                .withCacheConfiguration("product_v2",
                                                RedisCacheConfiguration.defaultCacheConfig()
                                                                .entryTtl(Duration.ofHours(1))
                                                                .serializeKeysWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new StringRedisSerializer()))
                                                                .serializeValuesWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer())))
                                .withCacheConfiguration("userDetails",
                                                RedisCacheConfiguration.defaultCacheConfig()
                                                                .entryTtl(Duration.ofMinutes(15))
                                                                .serializeKeysWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new StringRedisSerializer()))
                                                                .serializeValuesWith(
                                                                                RedisSerializationContext.SerializationPair
                                                                                                .fromSerializer(new GenericJackson2JsonRedisSerializer())));
        }
}
