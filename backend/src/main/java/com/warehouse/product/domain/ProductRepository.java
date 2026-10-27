package com.warehouse.product.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "supplier")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "supplier")
    Optional<Product> findWithSupplierById(Long id);

    @EntityGraph(attributePaths = "supplier")
    List<Product> findWithSupplierByIdIn(Collection<Long> ids);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("select distinct p.category from Product p order by p.category")
    List<String> findCategories();

    long countByArchivedFalse();
}
