package com.warehouse.stock.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

    @Override
    @EntityGraph(attributePaths = {"product", "warehouse", "user"})
    Page<StockMovement> findAll(Specification<StockMovement> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse", "user"})
    Optional<StockMovement> findWithDetailsById(Long id);

    boolean existsByReversesId(Long reversesId);

    /** Of the given movement ids, those that have already been reversed. */
    @Query("select m.reversesId from StockMovement m where m.reversesId in :ids")
    List<Long> findReversedAmong(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"product", "warehouse", "user"})
    List<StockMovement> findByReferenceTypeAndReferenceIdOrderByIdAsc(ReferenceType referenceType, Long referenceId);
}
