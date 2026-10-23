
package com.grocerymate.service;

import com.grocerymate.entity.PurchaseRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class VendorAssignmentService {

    private final JdbcTemplate jdbcTemplate;

    public VendorAssignmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Assignment(
            Long vendorId,
            Long productId,
            BigDecimal unitPrice
    ) {}


    public Assignment assignVendor(PurchaseRequest request) {
        String sql = """
            SELECT vp.vendor_id, vp.product_id, vp.price
            FROM vendor_products vp
            JOIN items_catalog ic
                ON ic.catalog_item_id = vp.catalog_item_id
            WHERE vp.is_available = 1
              AND vp.stock_quantity >= ?
              AND LOWER(TRIM(vp.unit)) = LOWER(TRIM(?))
              AND ic.catalog_item_id = COALESCE(
                  (
                      SELECT hi.catalog_item_id
                      FROM household_items hi
                      WHERE hi.item_id = ?
                  ),
                  (
                      SELECT ic2.catalog_item_id
                      FROM items_catalog ic2
                      WHERE ic2.normalized_item_name =
                            LOWER(TRIM(?))
                      LIMIT 1
                  )
              )
            ORDER BY vp.price ASC,
                     vp.stock_quantity DESC,
                     vp.product_id ASC
            LIMIT 1
            """;

        List<Assignment> matches = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Assignment(
                        rs.getLong("vendor_id"),
                        rs.getLong("product_id"),
                        rs.getBigDecimal("price")
                ),
                request.getRequestedQuantity(),
                request.getUnit(),
                request.getItemId(),
                request.getRequestedItemName()
        );

        if (matches.isEmpty()) {
            request.setAssignedVendorId(null);
            request.setStatus("UNASSIGNED");
            return null;
        }

        Assignment selected = matches.get(0);

        request.setAssignedVendorId(selected.vendorId());
        request.setStatus("OPEN");

        return selected;
    }
}
