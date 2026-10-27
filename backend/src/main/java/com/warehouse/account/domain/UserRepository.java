package com.warehouse.account.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "warehouses")
    Optional<User> findByEmail(String email);

    /** Loads the user with assigned warehouses in one query (used on every authenticated request). */
    @EntityGraph(attributePaths = "warehouses")
    Optional<User> findWithWarehousesById(Long id);

    @EntityGraph(attributePaths = "warehouses")
    List<User> findAllByOrderByRoleAscFullNameAsc();

    boolean existsByEmail(String email);
}
