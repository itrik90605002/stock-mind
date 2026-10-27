package com.warehouse.warehouse.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findAllByOrderByCodeAsc();

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);
}
