package com.economato.inventory.infrastructure.config.cache;

import java.util.Objects;
import java.util.concurrent.Callable;

import org.springframework.cache.Cache;

public class TwoLevelCache implements Cache {

    private final Cache l1Cache;
    private final Cache l2Cache;

    public TwoLevelCache(Cache l1Cache, Cache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    @Override
    public String getName() {
        return l2Cache.getName();
    }

    @Override
    public Object getNativeCache() {
        return l2Cache.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper l1Value = l1Cache.get(key);
        if (l1Value != null) {
            return l1Value;
        }

        ValueWrapper l2Value = l2Cache.get(key);
        if (l2Value != null) {
            l1Cache.put(key, l2Value.get());
        }
        return l2Value;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T l1Value = l1Cache.get(key, type);
        if (l1Value != null) {
            return l1Value;
        }

        T l2Value = l2Cache.get(key, type);
        if (l2Value != null) {
            l1Cache.put(key, l2Value);
        }
        return l2Value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper l1Value = l1Cache.get(key);
        if (l1Value != null) {
            @SuppressWarnings("unchecked")
            T casted = (T) l1Value.get();
            return casted;
        }

        T loaded = l2Cache.get(key, valueLoader);
        if (loaded != null) {
            l1Cache.put(key, loaded);
        }
        return loaded;
    }

    @Override
    public void put(Object key, Object value) {
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = l1Cache.putIfAbsent(key, value);
        if (existing == null) {
            l2Cache.putIfAbsent(key, value);
            return null;
        }

        ValueWrapper l2Existing = l2Cache.get(key);
        if (l2Existing != null && !Objects.equals(existing.get(), l2Existing.get())) {
            l1Cache.put(key, l2Existing.get());
            return l2Existing;
        }
        return existing;
    }

    @Override
    public void evict(Object key) {
        l1Cache.evict(key);
        l2Cache.evict(key);
    }

    @Override
    public void clear() {
        l1Cache.clear();
        l2Cache.clear();
    }
}
