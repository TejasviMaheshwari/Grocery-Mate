
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

    // Resolve a canonical catalog item using its name or an alias.
    private Long resolveCatalogItemId(String productName) {

        if (productName == null || productName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product name is required."
            );
        }

        String normalizedName =
                productName.trim().toLowerCase(Locale.ROOT);

        List<Long> catalogIds = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT c.catalog_item_id
                FROM items_catalog c
                LEFT JOIN catalog_item_aliases a
                    ON a.catalog_item_id = c.catalog_item_id
                WHERE (
                    c.normalized_item_name = ?
                    OR a.normalized_alias = ?
                )
                AND c.catalog_item_id <> 1
                """,
                Long.class,
                normalizedName,
                normalizedName
        );

        if (catalogIds.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This name matches multiple catalog products. "
                            + "Please use a more specific product name."
            );
        }

        if (catalogIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This product does not exist in the item catalog. "
                            + "Please select a valid catalog product."
            );
        }

        return catalogIds.get(0);
    }


    private String getCatalogCategory(Long catalogItemId) {
        List<String> categories = jdbcTemplate.queryForList(
                """
                SELECT category
                FROM items_catalog
                WHERE catalog_item_id = ?
                """,
                String.class,
                catalogItemId
        );

        if (categories.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected catalog product does not exist."
            );
        }

        return categories.get(0);
    }

    // Add a new product
    public VendorProduct addProduct(VendorProduct product) {

        product.setProductId(null);


        Long catalogItemId =
                resolveCatalogItemId(product.getProductName());

        product.setCatalogItemId(catalogItemId);
        product.setCategory(getCatalogCategory(catalogItemId));

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

        existingProduct.setCatalogItemId(catalogItemId);
        existingProduct.setCategory(getCatalogCategory(catalogItemId));
        existingProduct.setProductName(updatedProduct.getProductName());
        existingProduct.setDescription(updatedProduct.getDescription());
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