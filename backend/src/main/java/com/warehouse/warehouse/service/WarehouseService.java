package com.warehouse.warehouse.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.purchasing.domain.PurchaseOrderRepository;
import com.warehouse.purchasing.domain.PurchaseOrderStatus;
import com.warehouse.security.AuthUser;
import com.warehouse.stock.domain.StockLevelRepository;
import com.warehouse.transfer.domain.TransferRepository;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.warehouse.domain.Warehouse;
import com.warehouse.warehouse.domain.WarehouseRepository;
import com.warehouse.warehouse.dto.WarehousePatchRequest;
import com.warehouse.warehouse.dto.WarehouseRequest;
import com.warehouse.warehouse.dto.WarehouseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

@Service
public class WarehouseService {

    private final WarehouseRepository repository;
    private final StockLevelRepository stockLevelRepository;
    private final TransferRepository transferRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Clock clock;

    public WarehouseService(WarehouseRepository repository, StockLevelRepository stockLevelRepository,
                            TransferRepository transferRepository, PurchaseOrderRepository purchaseOrderRepository,
                            Clock clock) {
        this.repository = repository;
        this.stockLevelRepository = stockLevelRepository;
        this.transferRepository = transferRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(AuthUser user) {
        return repository.findAllByOrderByCodeAsc().stream()
                .map(w -> WarehouseResponse.from(w, user.canAccess(w.getId()))).toList();
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        String code = Warehouse.normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new BusinessRuleException("WAREHOUSE_CODE_TAKEN", "Warehouse code " + code + " is already in use");
        }
        return WarehouseResponse.from(repository.save(new Warehouse(code, request.name().trim(),
                blankToNull(request.address()), clock.instant())), true);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = find(id);
        if (!warehouse.getCode().equals(Warehouse.normalizeCode(request.code()))) {
            throw new BusinessRuleException("WAREHOUSE_CODE_IMMUTABLE", "A warehouse code cannot be changed");
        }
        warehouse.update(request.name().trim(), blankToNull(request.address()));
        return WarehouseResponse.from(warehouse, true);
    }

    @Transactional
    public WarehouseResponse patch(Long id, WarehousePatchRequest request) {
        if (request.details() == null && request.active() == null) {
            throw new IllegalArgumentException("Send details and/or active");
        }
        WarehouseResponse result = request.details() == null ? null : update(id, request.details());
        if (request.active() != null) {
            result = request.active() ? activate(id) : deactivate(id);
        }
        return result;
    }

    @Transactional
    public WarehouseResponse deactivate(Long id) {
        Warehouse warehouse = find(id);
        if (stockLevelRepository.totalOnHandInWarehouse(id) > 0) {
            throw new BusinessRuleException("WAREHOUSE_NOT_EMPTY",
                    "Move or write off all stock in " + warehouse.getCode() + " before deactivating it");
        }
        if (transferRepository.countInvolving(id, EnumSet.of(TransferStatus.DRAFT, TransferStatus.DISPATCHED)) > 0) {
            throw new BusinessRuleException("WAREHOUSE_HAS_OPEN_TRANSFERS",
                    warehouse.getCode() + " has open transfers");
        }
        if (purchaseOrderRepository.countByWarehouseIdAndStatusIn(id, PurchaseOrderStatus.OPEN) > 0) {
            throw new BusinessRuleException("WAREHOUSE_HAS_OPEN_POS", warehouse.getCode() + " has open purchase orders");
        }
        warehouse.deactivate();
        return WarehouseResponse.from(warehouse, true);
    }

    @Transactional
    public WarehouseResponse activate(Long id) {
        Warehouse warehouse = find(id);
        warehouse.activate();
        return WarehouseResponse.from(warehouse, true);
    }

    private Warehouse find(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Warehouse", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
