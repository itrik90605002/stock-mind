package com.warehouse.transfer.dto;

import com.warehouse.account.domain.User;
import com.warehouse.product.dto.ProductRef;
import com.warehouse.transfer.domain.Transfer;
import com.warehouse.transfer.domain.TransferLine;
import com.warehouse.transfer.domain.TransferStatus;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.time.Instant;
import java.util.List;

/** @param lines empty in list responses, populated in detail responses */
public record TransferResponse(Long id, WarehouseRef source, WarehouseRef destination, TransferStatus status,
                               String note, int totalUnits, int lineCount, String createdBy, Instant createdAt,
                               String dispatchedBy, Instant dispatchedAt, String receivedBy, Instant receivedAt,
                               Instant cancelledAt, List<Line> lines) {

    /** @param shortfall units lost in transit (dispatched but not received) */
    public record Line(Long id, ProductRef product, int quantity, Integer receivedQuantity, int shortfall) {

        static Line from(TransferLine l) {
            return new Line(l.getId(), ProductRef.from(l.getProduct()), l.getQuantity(), l.getReceivedQuantity(),
                    l.shortfall());
        }
    }

    public static TransferResponse summary(Transfer t) {
        return new TransferResponse(t.getId(), WarehouseRef.from(t.getSource()), WarehouseRef.from(t.getDestination()),
                t.getStatus(), t.getNote(), t.totalUnits(), t.getLines().size(), name(t.getCreatedBy()),
                t.getCreatedAt(), null, t.getDispatchedAt(), null, t.getReceivedAt(), t.getCancelledAt(), List.of());
    }

    public static TransferResponse detail(Transfer t) {
        return new TransferResponse(t.getId(), WarehouseRef.from(t.getSource()), WarehouseRef.from(t.getDestination()),
                t.getStatus(), t.getNote(), t.totalUnits(), t.getLines().size(), name(t.getCreatedBy()),
                t.getCreatedAt(), name(t.getDispatchedBy()), t.getDispatchedAt(), name(t.getReceivedBy()),
                t.getReceivedAt(), t.getCancelledAt(), t.getLines().stream().map(Line::from).toList());
    }

    private static String name(User user) {
        return user == null ? null : user.getFullName();
    }
}
