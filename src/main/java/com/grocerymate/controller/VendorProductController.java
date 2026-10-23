
package com.grocerymate.controller;

import com.grocerymate.entity.VendorProduct;
import com.grocerymate.service.VendorProductService;
import com.grocerymate.security.AuthenticatedAccount;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendor/products")
public class VendorProductController {

    private final VendorProductService service;

    public VendorProductController(VendorProductService service) {
        this.service = service;
    }

    // Get products belonging to the logged-in vendor
    @GetMapping
    public List<VendorProduct> getProducts(
            @AuthenticationPrincipal AuthenticatedAccount account) {

        return service.getProductsByVendor(account.id());
    }

    // Add a new product
    @PostMapping
    public VendorProduct addProduct(
            @RequestBody VendorProduct product,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        product.setVendorId(account.id());

        return service.addProduct(product);
    }

    // Edit an existing product
    @PutMapping("/{productId}")
    public VendorProduct updateProduct(
            @PathVariable Long productId,
            @RequestBody VendorProduct product,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        return service.updateProduct(
                productId,
                account.id(),
                product
        );
    }

    // Delete a product
    @DeleteMapping("/{productId}")
    public void deleteProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal AuthenticatedAccount account) {

        service.deleteProduct(productId, account.id());
    }
}
