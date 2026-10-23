
package com.grocerymate.repository;

import com.grocerymate.entity.VendorProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VendorProductRepository
        extends JpaRepository<VendorProduct, Long> {

    List<VendorProduct> findByVendorId(Long vendorId);
}
