package com.warehouse.supplier.service;

import com.warehouse.common.exception.BusinessRuleException;
import com.warehouse.common.exception.NotFoundException;
import com.warehouse.supplier.domain.Supplier;
import com.warehouse.supplier.domain.SupplierRepository;
import com.warehouse.supplier.dto.SupplierRequest;
import com.warehouse.supplier.dto.SupplierResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository repository;
    private final Clock clock;

    public SupplierService(SupplierRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(SupplierResponse::from).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (repository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessRuleException("SUPPLIER_EXISTS", "A supplier with this name already exists");
        }
        return SupplierResponse.from(repository.save(new Supplier(request.name(), blankToNull(request.contactEmail()),
                blankToNull(request.phone()), request.leadTimeDays(), clock.instant())));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = find(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new BusinessRuleException("SUPPLIER_EXISTS", "A supplier with this name already exists");
        }
        supplier.update(request.name(), blankToNull(request.contactEmail()), blankToNull(request.phone()),
                request.leadTimeDays());
        return SupplierResponse.from(supplier);
    }

    public Supplier find(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
