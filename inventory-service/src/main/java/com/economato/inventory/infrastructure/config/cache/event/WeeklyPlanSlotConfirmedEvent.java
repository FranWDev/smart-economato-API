package com.economato.inventory.infrastructure.config.cache.event;

import java.util.Set;

public record WeeklyPlanSlotConfirmedEvent(Set<Integer> affectedProductIds) {
}
