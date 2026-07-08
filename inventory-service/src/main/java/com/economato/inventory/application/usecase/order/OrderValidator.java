package com.economato.inventory.application.usecase.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.economato.inventory.application.dto.order.request.OrderReceptionDetailRequestDTO;
import com.economato.inventory.application.dto.order.request.OrderReceptionRequestDTO;
import com.economato.inventory.application.dto.order.request.OrdersByProductsRequestDTO;
import com.economato.inventory.application.dto.shared.request.LotReceptionRequestDTO;
import com.economato.inventory.domain.model.crisis.FoodCrisis;
import com.economato.inventory.domain.model.order.Order;
import com.economato.inventory.domain.model.order.OrderDetail;
import com.economato.inventory.domain.model.order.OrderStatus;
import com.economato.inventory.infrastructure.adapter.in.web.order.OrderReceptionAlreadyProcessedException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.InvalidOperationException;
import com.economato.inventory.infrastructure.adapter.in.web.shared.ResourceNotFoundException;
import com.economato.inventory.infrastructure.adapter.out.persistence.repository.crisis.FoodCrisisRepository;
import com.economato.inventory.infrastructure.config.web.shared.I18nService;
import com.economato.inventory.infrastructure.config.web.shared.MessageKey;

@Component
public class OrderValidator {

    private final I18nService i18nService;
    private final FoodCrisisRepository foodCrisisRepository;
    private final OrderReviewLockService orderReviewLockService;

    public OrderValidator(I18nService i18nService,
                          FoodCrisisRepository foodCrisisRepository,
                          OrderReviewLockService orderReviewLockService) {
        this.i18nService = i18nService;
        this.foodCrisisRepository = foodCrisisRepository;
        this.orderReviewLockService = orderReviewLockService;
    }

    public void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_CONSUMPTION_INVALID_DATE_RANGE));
        }
    }

    public List<Integer> validateProductsRequest(OrdersByProductsRequestDTO requestDTO) {
        if (requestDTO == null || requestDTO.getProductIds() == null || requestDTO.getProductIds().isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_SEARCH_MISSING_PRODUCTS));
        }

        List<Integer> productIds = requestDTO.getProductIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_SEARCH_INVALID_IDS));
        }
        return productIds;
    }

    public void validateReception(Order order, OrderReceptionRequestDTO receptionData) {
        orderReviewLockService.assertCanProcessReception(receptionData.getOrderId());

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.INCOMPLETE) {
            throw new OrderReceptionAlreadyProcessedException(order.getId(), order.getStatus());
        }

        if (order.getStatus() != OrderStatus.REVIEW) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_INVALID_STATE, order.getStatus()));
        }

        if (foodCrisisRepository != null
                && order.getSupplier() != null
                && foodCrisisRepository.existsByStatusAndSupplierId(FoodCrisis.CrisisStatus.ACTIVE,
                                order.getSupplier().getId())) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_SUPPLIER_IN_CRISIS));
        }
    }

    public void validateLotsSum(OrderDetail detail, OrderReceptionDetailRequestDTO receptionItem) {
        BigDecimal lotsSum = receptionItem.getLots().stream()
                .map(LotReceptionRequestDTO::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lotsSum.compareTo(receptionItem.getQuantityReceived()) != 0) {
            throw new InvalidOperationException(
                    i18nService.getMessage(MessageKey.ERROR_ORDER_LOTS_SUM_MISMATCH));
        }
    }

    public void validateTransition(Order order, OrderStatus newStatus) {
        orderReviewLockService.assertCanTransitionOrder(order.getId(), newStatus);

        if ((order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.INCOMPLETE)
                && (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.INCOMPLETE)) {
            throw new OrderReceptionAlreadyProcessedException(order.getId(), order.getStatus());
        }
    }

    public void validateMissingItemsStatus(Order order) {
        if (!OrderStatus.INCOMPLETE.equals(order.getStatus())) {
            throw new InvalidOperationException(i18nService.getMessage(MessageKey.ERROR_ORDER_INCOMPLETE_ONLY_MISSING_ITEMS));
        }
    }
}
