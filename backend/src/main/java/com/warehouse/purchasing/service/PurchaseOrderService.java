package com.warehouse.purchasing.service;

import com.warehouse.account.domain.UserRepository;
import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.common.web.PageResponse;
import com.warehouse.product.domain.Product;
import com.warehouse.product.domain.ProductRepository;
import com.warehouse.purchasing.domain.PurchaseOrder;
import com.warehouse.purchasing.domain.PurchaseOrderLine;
import com.warehouse.purchasing.domain.PurchaseOrderRepository;
import com.warehouse.purchasing.domain.PurchaseOrderStatus;
import com.warehouse.purchasing.dto.CreatedPurchaseOrders;
import com.warehouse.purchasing.dto.PurchaseOrderRequest;
import com.warehouse.purchasing.dto.PurchaseOrderResponse;
import com.warehouse.purchasing.dto.ReceivePurchaseOrderRequest;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.service.StockCommandExecutor;
import com.warehouse.stock.service.StockLedgerService;
import com.warehouse.supplier.domain.Supplier;
import com.warehouse.supplier.domain.SupplierRepository;
import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.warehouse.purchasing.domain.PurchaseOrderSpecifications.fromSupplier;
import static com.warehouse.purchasing.domain.PurchaseOrderSpecifications.inWarehouses;
import static com.warehouse.purchasing.domain.PurchaseOrderSpecifications.openOnly;
import static com.warehouse.purchasing.domain.PurchaseOrderSpecifications.withStatus;

/**
 * Purchase orders. Receipts book RECEIPT movements through the ledger in the same transaction
 * as the order update, via {@link StockCommandExecutor} (retries + idempotency).
 */
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockLedgerService ledger;
    private final StockCommandExecutor executor;
    private final WarehouseAccess access;
    private final Clock clock;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository, SupplierRepository supplierRepository,
                                WarehouseRepository warehouseRepository, ProductRepository productRepository,
                                UserRepository userRepository, StockLedgerService ledger,
                                StockCommandExecutor executor, WarehouseAccess access, Clock clock) {
        this.orderRepository = orderRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.ledger = ledger;
        this.executor = executor;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(AuthUser user, PurchaseOrderStatus status, boolean open,
                                                    Long warehouseId, Long supplierId, int page, int size) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<PurchaseOrder> spec = Specification.allOf(inWarehouses(scope), withStatus(status),
                openOnly(open), fromSupplier(supplierId));
        return PageResponse.from(orderRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), PurchaseOrderResponse::summary);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(AuthUser user, Long id) {
        PurchaseOrder po = find(id);
        access.require(user, po.getWarehouse().getId());
        return PurchaseOrderResponse.detail(po);
    }

    public PurchaseOrderResponse create(PurchaseOrderRequest request, AuthUser user, String idempotencyKey) {
        return createDrafts(List.of(request), user, idempotencyKey).orders().getFirst();
    }

    /** Creates several drafts atomically: either all are created or none. */
    public CreatedPurchaseOrders createDrafts(List<PurchaseOrderRequest> requests, AuthUser user,
                                              String idempotencyKey) {
        requests.forEach(r -> access.require(user, r.warehouseId()));
        return executor.execute("po.create", user.id(), idempotencyKey, CreatedPurchaseOrders.class, () ->
                new CreatedPurchaseOrders(requests.stream().map(r -> {
                    PurchaseOrder po = new PurchaseOrder(supplier(r.supplierId()), activeWarehouse(r.warehouseId()),
                            r.expectedDate(), trimToNull(r.note()), userRepository.getReferenceById(user.id()),
                            clock.instant());
                    addLines(po, r.lines());
                    return PurchaseOrderResponse.detail(orderRepository.save(po));
                }).toList()));
    }

    /** Replaces a draft's header and lines. */
    public PurchaseOrderResponse revise(Long id, PurchaseOrderRequest request, AuthUser user) {
        access.require(user, request.warehouseId());
        return executor.execute("po.revise", user.id(), null, PurchaseOrderResponse.class, () -> {
            PurchaseOrder po = find(id);
            access.require(user, po.getWarehouse().getId());
            po.reviseDraft(supplier(request.supplierId()), activeWarehouse(request.warehouseId()),
                    request.expectedDate(), trimToNull(request.note()));
            // Delete the old lines before inserting new ones; Hibernate would otherwise insert first
            // and trip the (order, product) unique constraint.
            orderRepository.flush();
            addLines(po, request.lines());
            return PurchaseOrderResponse.detail(po);
        });
    }

    /** Places the order with the supplier. Expected date defaults to today + supplier lead time. */
    public PurchaseOrderResponse place(Long id, AuthUser user) {
        return executor.execute("po.place", user.id(), null, PurchaseOrderResponse.class, () -> {
            PurchaseOrder po = find(id);
            access.require(user, po.getWarehouse().getId());
            po.markOrdered(LocalDate.now(clock).plusDays(po.getSupplier().getLeadTimeDays()), clock.instant());
            return PurchaseOrderResponse.detail(po);
        });
    }

    /** Books a (possibly partial) delivery: one RECEIPT ledger entry per line received. */
    public PurchaseOrderResponse receive(Long id, ReceivePurchaseOrderRequest request, AuthUser user,
                                         String idempotencyKey) {
        Map<Long, Integer> units = new LinkedHashMap<>();
        request.lines().forEach(l -> {
            if (units.put(l.lineId(), l.quantity()) != null) {
                throw new IllegalArgumentException("Line " + l.lineId() + " is listed twice");
            }
        });
        return executor.execute("po.receive", user.id(), idempotencyKey, PurchaseOrderResponse.class, () -> {
            PurchaseOrder po = find(id);
            access.require(user, po.getWarehouse().getId());
            po.receive(units, clock.instant());
            String reason = "PO #" + id + " from " + po.getSupplier().getName();
            units.forEach((lineId, qty) -> {
                PurchaseOrderLine line = po.line(lineId);
                ledger.receive(line.getProduct().getId(), po.getWarehouse().getId(), qty,
                        ReferenceType.PURCHASE_ORDER, id, reason, user.id());
            });
            return PurchaseOrderResponse.detail(po);
        });
    }

    public PurchaseOrderResponse cancel(Long id, String reason, AuthUser user) {
        return executor.execute("po.cancel", user.id(), null, PurchaseOrderResponse.class, () -> {
            PurchaseOrder po = find(id);
            access.require(user, po.getWarehouse().getId());
            po.cancel(reason.trim(), clock.instant());
            return PurchaseOrderResponse.detail(po);
        });
    }

    public PurchaseOrderResponse closeShort(Long id, String reason, AuthUser user) {
        return executor.execute("po.close", user.id(), null, PurchaseOrderResponse.class, () -> {
            PurchaseOrder po = find(id);
            access.require(user, po.getWarehouse().getId());
            po.closeShort(reason.trim(), clock.instant());
            return PurchaseOrderResponse.detail(po);
        });
    }

    private void addLines(PurchaseOrder po, List<PurchaseOrderRequest.Line> lines) {
        for (PurchaseOrderRequest.Line line : lines) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> NotFoundException.of("Product", line.productId()));
            if (product.isArchived()) {
                throw new BusinessRuleException("PRODUCT_ARCHIVED", "Product " + product.getSku() + " is archived");
            }
            po.addLine(product, line.quantity(), line.unitCost() != null ? line.unitCost() : product.getUnitCost());
        }
    }

    private PurchaseOrder find(Long id) {
        return orderRepository.findDetailedById(id).orElseThrow(() -> NotFoundException.of("Purchase order", id));
    }

    private Supplier supplier(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private Warehouse activeWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow(() -> NotFoundException.of("Warehouse", id));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("WAREHOUSE_INACTIVE", "Warehouse " + warehouse.getCode() + " is deactivated");
        }
        return warehouse;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
