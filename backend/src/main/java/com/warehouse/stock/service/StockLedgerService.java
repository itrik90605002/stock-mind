package com.warehouse.stock.service;

import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.domain.StockLevel;
import com.warehouse.stock.domain.StockLevelRepository;
import com.warehouse.stock.domain.StockMovement;
import com.warehouse.stock.domain.StockMovementRepository;
import com.warehouse.stock.domain.StockMutation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The single gateway for changing stock. Each method updates the {@link StockLevel} and appends
 * the matching ledger entry in the caller's transaction, so the two can never diverge.
 *
 * <p>All methods require an existing transaction ({@link Propagation#MANDATORY}); commands reach
 * them through {@link StockCommandExecutor}, which adds retries and idempotency.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StockLedgerService {

    private final StockLevelRepository levelRepository;
    private final StockMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public StockLedgerService(StockLevelRepository levelRepository, StockMovementRepository movementRepository,
                              UserRepository userRepository, Clock clock) {
        this.levelRepository = levelRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public StockMovement receive(Long productId, Long warehouseId, int quantity, ReferenceType referenceType,
                                 Long referenceId, String reason, Long userId) {
        requirePositive(quantity);
        StockLevel level = level(productId, warehouseId);
        requireNotArchived(level);
        return save(StockMutation.change(level, MovementType.RECEIPT, quantity, reason, referenceType, referenceId,
                null, user(userId), now()));
    }

    public StockMovement issue(Long productId, Long warehouseId, int quantity, String reason, Long userId) {
        requirePositive(quantity);
        StockLevel level = level(productId, warehouseId);
        return save(StockMutation.change(level, MovementType.ISSUE, -quantity, reason, ReferenceType.MANUAL, null,
                null, user(userId), now()));
    }

    public StockMovement adjust(Long productId, Long warehouseId, int delta, String reason, Long reversesId,
                                Long userId) {
        StockLevel level = level(productId, warehouseId);
        return save(StockMutation.change(level, MovementType.ADJUSTMENT, delta, reason, ReferenceType.MANUAL, null,
                reversesId, user(userId), now()));
    }

    public void reserve(Long productId, Long warehouseId, int quantity) {
        requirePositive(quantity);
        StockLevel level = level(productId, warehouseId);
        requireNotArchived(level);
        StockMutation.reserve(level, quantity, now());
    }

    public void release(Long productId, Long warehouseId, int quantity) {
        requirePositive(quantity);
        StockMutation.release(level(productId, warehouseId), quantity, now());
    }

    public StockMovement dispatchReserved(Long productId, Long warehouseId, int quantity, Long transferId,
                                          String reason, Long userId) {
        requirePositive(quantity);
        StockLevel level = level(productId, warehouseId);
        return save(StockMutation.consumeReservation(level, quantity, reason, transferId, user(userId), now()));
    }

    public StockMovement transferIn(Long productId, Long warehouseId, int quantity, Long transferId, String reason,
                                    Long userId) {
        requirePositive(quantity);
        StockLevel level = level(productId, warehouseId);
        return save(StockMutation.change(level, MovementType.TRANSFER_IN, quantity, reason, ReferenceType.TRANSFER,
                transferId, null, user(userId), now()));
    }

    /**
     * Loads the stock row for a pair, creating it on first use. The insert is conflict-safe, so two
     * concurrent first movements for the same pair both end up updating the one row.
     */
    public StockLevel level(Long productId, Long warehouseId) {
        StockLevel level = levelRepository.findByProductIdAndWarehouseId(productId, warehouseId).orElseGet(() -> {
            levelRepository.insertIfAbsent(productId, warehouseId, now());
            return levelRepository.findByProductIdAndWarehouseId(productId, warehouseId).orElseThrow();
        });
        if (!level.getWarehouse().isActive()) {
            throw new BusinessRuleException("WAREHOUSE_INACTIVE",
                    "Warehouse " + level.getWarehouse().getCode() + " is deactivated");
        }
        return level;
    }

    private StockMovement save(StockMovement movement) {
        return movementRepository.save(movement);
    }

    private static void requireNotArchived(StockLevel level) {
        if (level.getProduct().isArchived()) {
            throw new BusinessRuleException("PRODUCT_ARCHIVED",
                    "Product " + level.getProduct().getSku() + " is archived");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private User user(Long userId) {
        return userId == null ? null : userRepository.getReferenceById(userId);
    }

    private Instant now() {
        return clock.instant();
    }
}
