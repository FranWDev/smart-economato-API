package com.economato.inventory.infrastructure.config.weeklyplan.cache.event;

import java.util.Set;

public record WeeklyPlanSlotConfirmedEvent(Set<Integer> affectedProductIds) {
}
