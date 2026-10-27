package com.warehouse.account.domain;

/**
 * User roles, from most to least privileged.
 * <ul>
 *   <li>{@code ADMIN}: manages users and warehouses, sees every warehouse.</li>
 *   <li>{@code MANAGER}: manages products, suppliers, transfers and purchase orders in assigned warehouses.</li>
 *   <li>{@code CLERK}: records issues, adjustments and receipts in assigned warehouses.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    MANAGER,
    CLERK
}
