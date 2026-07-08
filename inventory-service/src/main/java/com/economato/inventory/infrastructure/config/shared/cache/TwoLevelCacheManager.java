package com.economato.inventory.infrastructure.config.shared.cache;

import java.util.Collection;
import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;

public class TwoLevelCacheManager implements CacheManager {

    private static final Set<String> L1_ELIGIBLE_CACHES = Set.of(
            "allergen",
            "allergens_page",
            "supplier",
            "suppliers_page",
            "user",
            "userByEmail",
            "product",
            "recipe");

    private final CacheManager l2CacheManager;
    private final CacheManager l1CacheManager;
    private final CacheManager noOpCacheManager = new NoOpCacheManager();

    public TwoLevelCacheManager(CacheManager l2CacheManager, CacheManager l1CacheManager) {
        this.l2CacheManager = l2CacheManager;
        this.l1CacheManager = l1CacheManager;
    }

    @Override
    public Cache getCache(String name) {
        Cache l2Cache = l2CacheManager.getCache(name);
        if (l2Cache == null) {
            return noOpCacheManager.getCache(name);
        }

        if (!L1_ELIGIBLE_CACHES.contains(name)) {
            return l2Cache;
        }

        Cache l1Cache = l1CacheManager.getCache(name);
        if (l1Cache == null) {
            return l2Cache;
        }

        return new TwoLevelCache(l1Cache, l2Cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return l2CacheManager.getCacheNames();
    }
}
