
package com.grocerymate.service;

import com.grocerymate.entity.VendorProduct;
import com.grocerymate.repository.VendorProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class VendorProductService {

    private final VendorProductRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public VendorProductService(
            VendorProductRepository repository,
            JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Get products belonging to a vendor
    public List<VendorProduct> getProductsByVendor(Long vendorId) {
        return repository.findByVendorId(vendorId);
    }

    // Resolve the canonical catalog item from its normalized name
    private Long resolveCatalogItemId(String productName) {

        if (productName == null || productName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product name is required."
            );
        }

        String normalizedName = productName.trim().toLowerCase(Locale.ROOT);

        List<Long> catalogIds = jdbcTemplate.queryForList(
                """
                SELECT catalog_item_id
                FROM items_catalog
                WHERE normalized_item_name = ?
                """,
                Long.class,
                normalizedName
        );

        if (catalogIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This product does not exist in the item catalog. "
                            + "Please use an existing catalog item name."
            );
        }

        return catalogIds.get(0);
    }

    // Add a new product
    public VendorProduct addProduct(VendorProduct product) {

        product.setProductId(null);

        Long catalogItemId =
                resolveCatalogItemId(product.getProductName());

        product.setCatalogItemId(catalogItemId);

        return repository.save(product);
    }

    // Update a product owned by the logged-in vendor
    public VendorProduct updateProduct(
            Long productId,
            Long vendorId,
            VendorProduct updatedProduct) {

        VendorProduct existingProduct = repository
                .findById(productId)
                .filter(product ->
                        Objects.equals(product.getVendorId(), vendorId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found or you do not own this product."
                ));

        // Resolve the catalog mapping for the updated name
        Long catalogItemId =
                resolveCatalogItemId(updatedProduct.getProductName());

        existingProduct.setProductName(updatedProduct.getProductName());
        existingProduct.setCatalogItemId(catalogItemId);
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setStockQuantity(updatedProduct.getStockQuantity());
        existingProduct.setUnit(updatedProduct.getUnit());
        existingProduct.setAvailable(updatedProduct.getAvailable());

        return repository.save(existingProduct);
    }

    // Delete a product owned by the logged-in vendor
    public void deleteProduct(Long productId, Long vendorId) {

        VendorProduct existingProduct = repository
                .findById(productId)
                .filter(product ->
                        Objects.equals(product.getVendorId(), vendorId))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found or you do not own this product."
                ));

        repository.delete(existingProduct);
    }
}