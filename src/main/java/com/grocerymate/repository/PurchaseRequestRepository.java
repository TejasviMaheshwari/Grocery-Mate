
package com.grocerymate.repository;

import com.grocerymate.entity.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequest, Long> {

    List<PurchaseRequest>
    findByAssignedVendorIdAndStatusOrderByRequestedAtDesc(
            Long assignedVendorId,
            String status
    );

    List<PurchaseRequest>
    findByHouseholdIdOrderByRequestedAtDesc(Long householdId);

    Optional<PurchaseRequest> findByRequestIdAndAssignedVendorId(
            Long requestId,
            Long assignedVendorId
    );

    @Modifying
    @Query("""
        UPDATE PurchaseRequest r
        SET r.status = 'ACCEPTED',
            r.claimedAt = :claimedAt
        WHERE r.requestId = :requestId
          AND r.assignedVendorId = :vendorId
          AND r.status = 'OPEN'
        """)
    int claimOpenRequest(
            @Param("requestId") Long requestId,
            @Param("vendorId") Long vendorId,
            @Param("claimedAt") LocalDateTime claimedAt
    );
}
