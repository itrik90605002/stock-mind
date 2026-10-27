package com.warehouse.stock.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long>, JpaSpecificationExecutor<StockLevel> {

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Override
    @EntityGraph(attributePaths = {"product", "warehouse"})
    Page<StockLevel> findAll(Specification<StockLevel> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    List<StockLevel> findByProductIdAndWarehouseIdInOrderByWarehouseCodeAsc(Long productId, Collection<Long> warehouseIds);

    /**
     * Creates the row for a product/warehouse pair if it does not exist. {@code ON CONFLICT DO NOTHING}
     * makes concurrent first movements for a new pair safe without locking.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stock_levels (product_id, warehouse_id, on_hand, reserved, reorder_point, version, updated_at)
            VALUES (:productId, :warehouseId, 0, 0, 0, 0, :now)
            ON CONFLICT (product_id, warehouse_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                        @Param("now") Instant now);

    @Query("select coalesce(sum(s.onHand), 0) from StockLevel s where s.warehouse.id = :warehouseId")
    long totalOnHandInWarehouse(@Param("warehouseId") Long warehouseId);
}
