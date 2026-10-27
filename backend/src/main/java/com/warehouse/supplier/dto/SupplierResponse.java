package com.warehouse.supplier.dto;

import com.warehouse.supplier.domain.Supplier;

public record SupplierResponse(Long id, String name, String contactEmail, String phone, int leadTimeDays) {

    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactEmail(), s.getPhone(), s.getLeadTimeDays());
    }
}
