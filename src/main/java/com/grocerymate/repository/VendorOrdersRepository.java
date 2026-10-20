package com.grocerymate.repository;

import com.grocerymate.entity.VendorOrders;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorOrdersRepository extends JpaRepository<VendorOrders, Long> {
}
