package com.warehouse.purchasing.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>,
        JpaSpecificationExecutor<PurchaseOrder> {

    @Override
    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy"})
    Page<PurchaseOrder> findAll(Specification<PurchaseOrder> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"supplier", "warehouse", "createdBy", "lines", "lines.product"})
    Optional<PurchaseOrder> findDetailedById(Long id);

    long countByWarehouseIdAndStatusIn(Long warehouseId, Collection<PurchaseOrderStatus> statuses);
}
