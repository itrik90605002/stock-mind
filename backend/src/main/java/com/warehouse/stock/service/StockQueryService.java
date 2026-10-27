package com.warehouse.stock.service;

import com.warehouse.common.exception.NotFoundException;
import com.warehouse.common.web.PageResponse;
import com.warehouse.security.AuthUser;
import com.warehouse.security.WarehouseAccess;
import com.warehouse.stock.domain.MovementType;
import com.warehouse.stock.domain.ReferenceType;
import com.warehouse.stock.domain.StockLevel;
import com.warehouse.stock.domain.StockLevelRepository;
import com.warehouse.stock.domain.StockLevelSpecifications;
import com.warehouse.stock.domain.StockMovement;
import com.warehouse.stock.domain.StockMovementRepository;
import com.warehouse.stock.domain.StockMovementSpecifications;
import com.warehouse.stock.dto.MovementResponse;
import com.warehouse.stock.dto.StockRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.warehouse.stock.domain.StockLevelSpecifications.activeProducts;
import static com.warehouse.stock.domain.StockLevelSpecifications.inCategory;
import static com.warehouse.stock.domain.StockLevelSpecifications.lowOnly;
import static com.warehouse.stock.domain.StockLevelSpecifications.productMatches;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    private final StockLevelRepository levelRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseAccess access;

    public StockQueryService(StockLevelRepository levelRepository, StockMovementRepository movementRepository,
                             WarehouseAccess access) {
        this.levelRepository = levelRepository;
        this.movementRepository = movementRepository;
        this.access = access;
    }

    public PageResponse<StockRow> stock(AuthUser user, Long warehouseId, String q, String category, boolean low,
                                        int page, int size) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<StockLevel> spec = Specification.allOf(
                StockLevelSpecifications.inWarehouses(scope), activeProducts(),
                productMatches(q), inCategory(category), lowOnly(low));
        PageRequest pageable = PageRequest.of(page, size, Sort.by("product.sku", "warehouse.code"));
        return PageResponse.from(levelRepository.findAll(spec, pageable), StockRow::from);
    }

    public List<StockRow> forProduct(AuthUser user, Long productId) {
        List<Long> scope = access.scope(user, null);
        if (scope.isEmpty()) {
            return List.of();
        }
        return levelRepository.findByProductIdAndWarehouseIdInOrderByWarehouseCodeAsc(productId, scope).stream()
                .map(StockRow::from).toList();
    }

    public PageResponse<MovementResponse> movements(AuthUser user, Long warehouseId, Long productId,
                                                    MovementType type, ReferenceType referenceType,
                                                    Long referenceId, Instant from, Instant to, int page, int size) {
        List<Long> scope = access.scope(user, warehouseId);
        if (scope.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }
        Specification<StockMovement> spec = Specification.allOf(
                StockMovementSpecifications.inWarehouses(scope),
                StockMovementSpecifications.forProduct(productId),
                StockMovementSpecifications.ofType(type),
                StockMovementSpecifications.referencing(referenceType, referenceId),
                StockMovementSpecifications.from(from),
                StockMovementSpecifications.before(to));
        Page<StockMovement> result = movementRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Set<Long> reversed = reversedAmong(result.getContent());
        return PageResponse.from(result, m -> MovementResponse.from(m, reversed.contains(m.getId())));
    }

    public MovementResponse movement(AuthUser user, Long id) {
        StockMovement m = movementRepository.findWithDetailsById(id)
                .orElseThrow(() -> NotFoundException.of("Movement", id));
        access.require(user, m.getWarehouse().getId());
        return MovementResponse.from(m, movementRepository.existsByReversesId(id));
    }

    private Set<Long> reversedAmong(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(movementRepository.findReversedAmong(movements.stream().map(StockMovement::getId).toList()));
    }
}
