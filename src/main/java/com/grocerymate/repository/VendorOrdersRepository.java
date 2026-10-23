
package com.grocerymate.repository;

import com.grocerymate.entity.VendorOrders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;


@Repository

public interface VendorOrdersRepository

        extends JpaRepository<VendorOrders, Long> {

    List<VendorOrders> findByVendorIdOrderByOrderedAtDesc(
            Long vendorId
    );

    Optional<VendorOrders> findByVendorOrderIdAndVendorId(
            Long vendorOrderId,
            Long vendorId
    );

    boolean existsByPurchaseRequestId(Long purchaseRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT o
    FROM VendorOrders o
    WHERE o.vendorOrderId = :orderId
      AND o.vendorId = :vendorId
    """)
    Optional<VendorOrders> findByVendorOrderIdAndVendorIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("vendorId") Long vendorId);

}

