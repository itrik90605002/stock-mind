package com.warehouse.stock.dto;

import com.warehouse.product.dto.ProductRef;
import com.warehouse.stock.domain.StockLevel;
import com.warehouse.warehouse.dto.WarehouseRef;

import java.math.BigDecimal;

/** One product in one warehouse, as shown on the stock page. */
public record StockRow(Long id, ProductRef product, String category, WarehouseRef warehouse, int onHand,
                       int reserved, int available, int reorderPoint, Integer leadTimeDays, boolean low,
                       BigDecimal unitCost, BigDecimal value, long version) {

    public static StockRow from(StockLevel s) {
        BigDecimal cost = s.getProduct().getUnitCost();
        return new StockRow(s.getId(), ProductRef.from(s.getProduct()), s.getProduct().getCategory(),
                WarehouseRef.from(s.getWarehouse()), s.getOnHand(), s.getReserved(), s.available(),
                s.getReorderPoint(), s.getLeadTimeDays(), s.isLow(), cost, cost.multiply(BigDecimal.valueOf(s.getOnHand())),
                s.getVersion());
    }
}
