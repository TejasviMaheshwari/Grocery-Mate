
package com.grocerymate.controller;

import com.grocerymate.entity.PurchaseRequest;
import com.grocerymate.repository.PurchaseRequestRepository;
import com.grocerymate.security.AuthenticatedAccount;
import com.grocerymate.service.VendorAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/household/requests")
public class HouseholdPurchaseRequestController {

    private final PurchaseRequestRepository requestRepository;
    private final VendorAssignmentService vendorAssignmentService;
    private final JdbcTemplate jdbcTemplate;

    public HouseholdPurchaseRequestController(
            PurchaseRequestRepository requestRepository,
            VendorAssignmentService vendorAssignmentService,
            JdbcTemplate jdbcTemplate) {
        this.requestRepository = requestRepository;
        this.vendorAssignmentService = vendorAssignmentService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CreateRequestBody(
            Long itemId,
            String requestedItemName,
            BigDecimal requestedQuantity,
            String unit,
            String notes
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<?> createRequest(
            @RequestBody CreateRequestBody body,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        if (account == null || account.householdId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message",
                            "Please log in with a household account."));
        }

        if (body.requestedQuantity() == null
                || body.requestedQuantity().compareTo(BigDecimal.ZERO) <= 0
                || body.unit() == null
                || body.unit().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "A positive quantity and unit are required."));
        }

        String itemName = body.requestedItemName();

        // If this request refers to an existing household item,
        // verify ownership and use its canonical name.
        if (body.itemId() != null) {
            var itemNames = jdbcTemplate.query(
                    """
                    SELECT item_name
                    FROM household_items
                    WHERE item_id = ?
                      AND household_id = ?
                      AND is_active = 1
                    """,
                    (rs, rowNum) -> rs.getString("item_name"),
                    body.itemId(),
                    account.householdId()
            );

            if (itemNames.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message",
                                "The selected item does not belong to your household."));
            }

            itemName = itemNames.get(0);
        }

        if (itemName == null || itemName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Please provide an item name."));
        }

        PurchaseRequest request = new PurchaseRequest();
        request.setHouseholdId(account.householdId());
        request.setRequestedBy(account.id());
        request.setItemId(body.itemId());
        request.setRequestedItemName(itemName.trim());
        request.setRequestedQuantity(body.requestedQuantity());
        request.setUnit(body.unit().trim());
        request.setNotes(body.notes());

        // Finds the cheapest vendor with a matching item,
        // unit and sufficient available stock.
        VendorAssignmentService.Assignment assignment =
                vendorAssignmentService.assignVendor(request);

        PurchaseRequest saved = requestRepository.save(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", assignment == null
                                ? "Request created, but no eligible vendor was found."
                                : "Purchase request created and assigned to a vendor.",
                        "request", saved,
                        "assigned", assignment != null
                )
        );

    }
}
