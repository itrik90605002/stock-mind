package com.warehouse.forecast.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.forecast.domain.StockForecastRepository;
import com.warehouse.forecast.dto.CreateOrdersFromSuggestionsRequest;
import com.warehouse.forecast.dto.ReorderSuggestion;
import com.warehouse.forecast.dto.ReorderSuggestionsResponse;
import com.warehouse.product.domain.Product;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.purchasing.dto.CreatedPurchaseOrders;
import com.warehouse.purchasing.dto.PurchaseOrderRequest;
import com.warehouse.purchasing.service.PurchaseOrderService;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReorderService {

    private final StockForecastRepository forecastRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final WarehouseAccess access;

    public ReorderService(StockForecastRepository forecastRepository, ProductRepository productRepository,
                          PurchaseOrderService purchaseOrderService, WarehouseAccess access) {
        this.forecastRepository = forecastRepository;
        this.productRepository = productRepository;
        this.purchaseOrderService = purchaseOrderService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public ReorderSuggestionsResponse suggestions(AuthUser user, Long warehouseId) {
        List<Long> scope = access.scope(user, warehouseId);
        List<ReorderSuggestion> suggestions = scope.isEmpty() ? List.of()
                : forecastRepository.findSuggestions(scope).stream().map(ReorderSuggestion::from).toList();
        return new ReorderSuggestionsResponse(forecastRepository.lastComputedAt().orElse(null), suggestions);
    }

    public CreatedPurchaseOrders createOrders(CreateOrdersFromSuggestionsRequest request, AuthUser user,
                                              String idempotencyKey) {
        Map<Long, Product> products = productRepository.findWithSupplierByIdIn(
                        request.items().stream().map(CreateOrdersFromSuggestionsRequest.Item::productId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        List<String> withoutSupplier = new ArrayList<>();
        record Key(Long supplierId, Long warehouseId) {
        }
        Map<Key, List<PurchaseOrderRequest.Line>> groups = new LinkedHashMap<>();
        for (CreateOrdersFromSuggestionsRequest.Item item : request.items()) {
            Product product = products.get(item.productId());
            if (product == null) {
                throw NotFoundException.of("Product", item.productId());
            }
            if (product.getSupplier() == null) {
                withoutSupplier.add(product.getSku());
                continue;
            }
            groups.computeIfAbsent(new Key(product.getSupplier().getId(), item.warehouseId()), k -> new ArrayList<>())
                    .add(new PurchaseOrderRequest.Line(product.getId(), item.quantity(), null));
        }
        if (!withoutSupplier.isEmpty()) {
            throw new BusinessRuleException("NO_SUPPLIER",
                    "Set a preferred supplier first for: " + String.join(", ", withoutSupplier));
        }
        List<PurchaseOrderRequest> orders = groups.entrySet().stream()
                .map(e -> new PurchaseOrderRequest(e.getKey().supplierId(), e.getKey().warehouseId(), null,
                        "Created from reorder suggestions", e.getValue()))
                .toList();
        return purchaseOrderService.createDrafts(orders, user, idempotencyKey);
    }
}
