
package com.grocerymate.controller;

import com.grocerymate.entity.PurchaseRequest;
import com.grocerymate.entity.VendorOrders;
import com.grocerymate.security.AuthenticatedAccount;
import com.grocerymate.service.VendorWorkflowService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/vendor")
public class VendorWorkflowController {

    private final VendorWorkflowService workflowService;

    public VendorWorkflowController(VendorWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    private Long requireVendor(AuthenticatedAccount account) {
        if (account == null || !"VENDOR".equalsIgnoreCase(account.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vendor account required.");
        }
        return account.id();
    }

    // GET /api/vendor/requests
    @GetMapping("/requests")
    public List<PurchaseRequest> getOpenRequests(
            @AuthenticationPrincipal AuthenticatedAccount account) {

        return workflowService.getOpenRequests(requireVendor(account));
    }

    // GET /api/vendor/orders
    @GetMapping("/orders")
    public List<VendorOrders> getOrders(
            @AuthenticationPrincipal AuthenticatedAccount account) {

        return workflowService.getVendorOrders(requireVendor(account));
    }

    // POST /api/vendor/requests/{requestId}/accept
    // JSON body: {"productId": 4}
    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<VendorOrders> acceptRequest(
            @PathVariable Long requestId,
            @RequestBody AcceptRequestBody body,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        if (body == null || body.productId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "productId is required.");
        }

        VendorOrders order = workflowService.acceptRequest(
                requestId,
                requireVendor(account),
                body.productId());

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // PATCH /api/vendor/orders/{orderId}/status
    // JSON body: {"status": "PROCESSING"}
    @PatchMapping("/orders/{orderId}/status")
    public VendorOrders updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody UpdateOrderStatusBody body,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        if (body == null || body.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status is required.");
        }

        return workflowService.updateOrderStatus(
                orderId,
                requireVendor(account),
                body.status());
    }

    public record AcceptRequestBody(Long productId) {}

    public record UpdateOrderStatusBody(String status) {}
}
