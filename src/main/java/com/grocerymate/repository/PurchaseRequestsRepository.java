package com.grocerymate.repository;

import com.grocerymate.entity.PurchaseRequests;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestsRepository extends JpaRepository<PurchaseRequests, Long> {
}
