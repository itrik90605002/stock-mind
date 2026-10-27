package com.warehouse.transfer.service;

import com.warehouse.account.domain.User;
import com.warehouse.account.domain.UserRepository;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.common.web.PageResponse;
import com.warehouse.product.domain.Product;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.service.StockCommandExecutor;
import com.warehouse.stock.service.StockLedgerService;
import com.warehouse.transfer.domain.Transfer;
import com.warehouse.transfer.domain.TransferLine;
import com.warehouse.transfer.domain.TransferRepository;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.transfer.dto.CreateTransferRequest;
import com.warehouse.transfer.dto.ReceiveTransferRequest;
import com.warehouse.transfer.dto.TransferLineRequest;
import com.warehouse.transfer.dto.TransferResponse;
import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.warehouse.transfer.domain.TransferSpecifications.involving;
import static com.warehouse.transfer.domain.TransferSpecifications.withStatus;

/**
 * Transfers between warehouses. Every state change and its stock effect run in one transaction
 * through {@link StockCommandExecutor}:
 * <ul>
 *   <li>create: reserve each line at the source (fails if not enough available stock)</li>
 *   <li>dispatch: TRANSFER_OUT at the source, consuming the reservation</li>
 *   <li>receive: TRANSFER_IN at the destination for what actually arrived; any shortfall stays
 *       recorded on the line as lost in transit</li>
 *   <li>cancel (draft only): release the reservation</li>
 * </ul>
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockLedgerService ledger;
    private final StockCommandExecutor executor;
    private final WarehouseAccess access;
    private final Clock clock;

    public TransferService(TransferRepository transferRepository, WarehouseRepository warehouseRepository,
                           ProductRepository productRepository, UserRepository userRepository,
                           StockLedgerService ledger, StockCommandExecutor executor, WarehouseAccess access,
                           Clock clock) {
        this.transferRepository = transferRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.ledger = ledger;
        this.executor = executor;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> list(AuthUser user, TransferStatus status, Long warehouseId, int page,
                                               int size) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<Transfer> spec = Specification.allOf(involving(scope), withStatus(status));
        return PageResponse.from(transferRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), TransferResponse::summary);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(AuthUser user, Long id) {
        Transfer transfer = find(id);
        requireInvolved(user, transfer);
        return TransferResponse.detail(transfer);
    }

    public TransferResponse create(CreateTransferRequest request, AuthUser user, String idempotencyKey) {
        access.require(user, request.sourceWarehouseId());
        requireDistinctProducts(request.lines());
        return executor.execute("transfer.create", user.id(), idempotencyKey, TransferResponse.class, () -> {
            Warehouse source = activeWarehouse(request.sourceWarehouseId());
            Warehouse destination = activeWarehouse(request.destinationWarehouseId());
            Transfer transfer = new Transfer(source, destination, trimToNull(request.note()), userRef(user),
                    clock.instant());
            // Fixed order (by product id) keeps concurrent multi-line transfers from interleaving oddly.
            List<TransferLineRequest> lines = request.lines().stream()
                    .sorted(Comparator.comparing(TransferLineRequest::productId)).toList();
            for (TransferLineRequest line : lines) {
                Product product = productRepository.findById(line.productId())
                        .orElseThrow(() -> NotFoundException.of("Product", line.productId()));
                transfer.addLine(product, line.quantity());
                ledger.reserve(product.getId(), source.getId(), line.quantity());
            }
            return TransferResponse.detail(transferRepository.save(transfer));
        });
    }

    public TransferResponse dispatch(Long id, AuthUser user, String idempotencyKey) {
        return executor.execute("transfer.dispatch", user.id(), idempotencyKey, TransferResponse.class, () -> {
            Transfer transfer = find(id);
            access.require(user, transfer.getSource().getId());
            transfer.markDispatched(userRef(user), clock.instant());
            String reason = "Transfer #" + id + " to " + transfer.getDestination().getCode();
            for (TransferLine line : transfer.getLines()) {
                ledger.dispatchReserved(line.getProduct().getId(), transfer.getSource().getId(), line.getQuantity(),
                        id, reason, user.id());
            }
            return TransferResponse.detail(transfer);
        });
    }

    public TransferResponse receive(Long id, ReceiveTransferRequest request, AuthUser user, String idempotencyKey) {
        Map<Long, Integer> received = new HashMap<>();
        if (request != null && request.lines() != null) {
            request.lines().forEach(l -> {
                if (received.put(l.lineId(), l.receivedQuantity()) != null) {
                    throw new IllegalArgumentException("Line " + l.lineId() + " is listed twice");
                }
            });
        }
        return executor.execute("transfer.receive", user.id(), idempotencyKey, TransferResponse.class, () -> {
            Transfer transfer = find(id);
            access.require(user, transfer.getDestination().getId());
            transfer.markReceived(received, userRef(user), clock.instant());
            String reason = "Transfer #" + id + " from " + transfer.getSource().getCode();
            for (TransferLine line : transfer.getLines()) {
                if (line.getReceivedQuantity() > 0) {
                    ledger.transferIn(line.getProduct().getId(), transfer.getDestination().getId(),
                            line.getReceivedQuantity(), id,
                            line.shortfall() > 0 ? reason + " (" + line.shortfall() + " short)" : reason, user.id());
                }
            }
            return TransferResponse.detail(transfer);
        });
    }

    public TransferResponse cancel(Long id, String reason, AuthUser user) {
        return executor.execute("transfer.cancel", user.id(), null, TransferResponse.class, () -> {
            Transfer transfer = find(id);
            access.require(user, transfer.getSource().getId());
            transfer.cancel(reason.trim(), clock.instant());
            for (TransferLine line : transfer.getLines()) {
                ledger.release(line.getProduct().getId(), transfer.getSource().getId(), line.getQuantity());
            }
            return TransferResponse.detail(transfer);
        });
    }

    private Transfer find(Long id) {
        return transferRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Transfer", id));
    }

    private void requireInvolved(AuthUser user, Transfer transfer) {
        if (!user.canAccess(transfer.getSource().getId()) && !user.canAccess(transfer.getDestination().getId())) {
            access.require(user, transfer.getSource().getId());
        }
    }

    private Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow(() -> NotFoundException.of("Warehouse", id));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("WAREHOUSE_INACTIVE", "Warehouse " + warehouse.getCode() + " is deactivated");
        }
        return warehouse;
    }

    private static void requireDistinctProducts(List<TransferLineRequest> lines) {
        Set<Long> seen = new HashSet<>();
        for (TransferLineRequest line : lines) {
            if (!seen.add(line.productId())) {
                throw new IllegalArgumentException("Each product may appear only once per transfer");
            }
        }
    }

    private User userRef(AuthUser user) {
        return userRepository.getReferenceById(user.id());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
