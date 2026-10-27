package com.warehouse.transfer.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long>, JpaSpecificationExecutor<Transfer> {

    @Override
    @EntityGraph(attributePaths = {"source", "destination", "createdBy"})
    Page<Transfer> findAll(Specification<Transfer> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"source", "destination", "createdBy", "dispatchedBy", "receivedBy",
            "lines", "lines.product"})
    Optional<Transfer> findDetailedById(Long id);

    @Query("""
            select count(t) from Transfer t
            where t.status in :statuses and (t.source.id = :warehouseId or t.destination.id = :warehouseId)
            """)
    long countInvolving(@Param("warehouseId") Long warehouseId, @Param("statuses") Collection<TransferStatus> statuses);
}
