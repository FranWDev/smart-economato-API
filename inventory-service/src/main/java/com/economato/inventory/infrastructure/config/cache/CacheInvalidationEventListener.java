package com.economato.inventory.infrastructure.config.cache;

import java.util.Set;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.economato.inventory.infrastructure.config.cache.event.ForecastUpdatedEvent;
import com.economato.inventory.infrastructure.config.cache.event.StockMovementEvent;
import com.economato.inventory.infrastructure.config.cache.event.WeeklyPlanSlotConfirmedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CacheInvalidationEventListener {

    private final CacheManager cacheManager;

    @EventListener
    public void onStockMovement(StockMovementEvent event) {
        evict("product", event.productId());
        clear("products_page");
        clear("stock_alerts");
        clear("product_stats");
        clear("orders_pending");
    }

    @EventListener
    public void onForecastUpdated(ForecastUpdatedEvent event) {
        clear("stock_alerts");
        clear("stock_predictions");
        clear("daily_forecast");
    }

    @EventListener
    public void onWeeklyPlanSlotConfirmed(WeeklyPlanSlotConfirmedEvent event) {
        clear("stock_alerts");
        clear("products_page");

        Set<Integer> productIds = event.affectedProductIds() == null ? Set.of() : event.affectedProductIds();
        for (Integer productId : productIds) {
            evict("product", productId);
        }
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null) {
            cache.evict(key);
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
