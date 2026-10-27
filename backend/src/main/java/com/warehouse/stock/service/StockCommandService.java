package com.warehouse.stock.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.domain.StockLevel;
import com.warehouse.stock.domain.StockMovement;
import com.warehouse.stock.domain.StockMovementRepository;
import com.warehouse.stock.dto.AdjustRequest;
import com.warehouse.stock.dto.IssueRequest;
import com.warehouse.stock.dto.MovementResponse;
import com.warehouse.stock.dto.StockRow;
import com.warehouse.stock.dto.StockSettingsRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Manual stock commands recorded by users: issues, adjustments, reversals and planning settings.
 * Each runs through {@link StockCommandExecutor} for retries and idempotency.
 */
@Service
public class StockCommandService {

    private final StockCommandExecutor executor;
    private final StockLedgerService ledger;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseAccess access;
    private final Clock clock;

    public StockCommandService(StockCommandExecutor executor, StockLedgerService ledger,
                               StockMovementRepository movementRepository, ProductRepository productRepository,
                               WarehouseAccess access, Clock clock) {
        this.executor = executor;
        this.ledger = ledger;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.access = access;
        this.clock = clock;
    }

    public MovementResponse issue(IssueRequest request, AuthUser user, String idempotencyKey) {
        access.require(user, request.warehouseId());
        requireProduct(request.productId());
        return executor.execute("stock.issue", user.id(), idempotencyKey, MovementResponse.class, () ->
                MovementResponse.from(ledger.issue(request.productId(), request.warehouseId(), request.quantity(),
                        trimToNull(request.reason()), user.id()), false));
    }

    public MovementResponse adjust(AdjustRequest request, AuthUser user, String idempotencyKey) {
        access.require(user, request.warehouseId());
        requireProduct(request.productId());
        return executor.execute("stock.adjust", user.id(), idempotencyKey, MovementResponse.class, () ->
                MovementResponse.from(ledger.adjust(request.productId(), request.warehouseId(),
                        request.quantityChange(), request.reason().trim(), null, user.id()), false));
    }

    /**
     * Records the opposite of a manual movement. Document-driven movements (purchase orders,
     * transfers) are corrected through their document instead, and each entry can be reversed once.
     */
    public MovementResponse reverse(Long movementId, String reason, AuthUser user, String idempotencyKey) {
        return executor.execute("stock.reverse", user.id(), idempotencyKey, MovementResponse.class, () -> {
            StockMovement original = movementRepository.findWithDetailsById(movementId)
                    .orElseThrow(() -> NotFoundException.of("Movement", movementId));
            access.require(user, original.getWarehouse().getId());
            if (original.getReferenceType() != ReferenceType.MANUAL) {
                throw new BusinessRuleException("NOT_REVERSIBLE",
                        "Only manual movements can be reversed; correct the purchase order or transfer instead");
            }
            if (original.getReversesId() != null) {
                throw new BusinessRuleException("NOT_REVERSIBLE", "A reversal cannot itself be reversed");
            }
            if (movementRepository.existsByReversesId(movementId)) {
                throw new BusinessRuleException("ALREADY_REVERSED", "This movement has already been reversed");
            }
            StockMovement reversal = ledger.adjust(original.getProduct().getId(), original.getWarehouse().getId(),
                    -original.getQuantity(), "Reversal of #" + movementId + ": " + reason.trim(), movementId, user.id());
            return MovementResponse.from(reversal, false);
        });
    }

    /** Sets reorder point and lead-time override for a product in a warehouse. */
    public StockRow updateSettings(StockSettingsRequest request, AuthUser user) {
        access.require(user, request.warehouseId());
        requireProduct(request.productId());
        return executor.execute("stock.settings", user.id(), null, StockRow.class, () -> {
            StockLevel level = ledger.level(request.productId(), request.warehouseId());
            level.updateSettings(request.reorderPoint(), request.leadTimeDays(), clock.instant());
            return StockRow.from(level);
        });
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw NotFoundException.of("Product", productId);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
