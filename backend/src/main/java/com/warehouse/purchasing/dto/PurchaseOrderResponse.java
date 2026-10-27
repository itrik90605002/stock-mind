package com.warehouse.purchasing.dto;

import com.warehouse.product.dto.ProductRef;
import com.warehouse.purchasing.domain.PurchaseOrder;
import com.warehouse.purchasing.domain.PurchaseOrderLine;
import com.warehouse.purchasing.domain.PurchaseOrderStatus;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** @param lines empty in list responses, populated in detail responses */
public record PurchaseOrderResponse(Long id, Long supplierId, String supplierName, WarehouseRef warehouse,
                                    PurchaseOrderStatus status, LocalDate expectedDate, String note,
                                    int totalUnits, int remainingUnits, BigDecimal totalValue, int lineCount,
                                    String createdBy, Instant createdAt, Instant orderedAt, Instant closedAt,
                                    List<Line> lines) {

    public record Line(Long id, ProductRef product, int quantity, int receivedQuantity, int remaining,
                       BigDecimal unitCost, BigDecimal lineTotal) {

        static Line from(PurchaseOrderLine l) {
            return new Line(l.getId(), ProductRef.from(l.getProduct()), l.getQuantity(), l.getReceivedQuantity(),
                    l.remaining(), l.getUnitCost(), l.lineTotal());
        }
    }

    public static PurchaseOrderResponse summary(PurchaseOrder po) {
        return of(po, List.of());
    }

    public static PurchaseOrderResponse detail(PurchaseOrder po) {
        return of(po, po.getLines().stream().map(Line::from).toList());
    }

    private static PurchaseOrderResponse of(PurchaseOrder po, List<Line> lines) {
        return new PurchaseOrderResponse(po.getId(), po.getSupplier().getId(), po.getSupplier().getName(),
                WarehouseRef.from(po.getWarehouse()), po.getStatus(), po.getExpectedDate(), po.getNote(),
                po.totalUnits(), po.remainingUnits(), po.totalValue(), po.getLines().size(),
                po.getCreatedBy().getFullName(), po.getCreatedAt(), po.getOrderedAt(), po.getClosedAt(), lines);
    }
}
