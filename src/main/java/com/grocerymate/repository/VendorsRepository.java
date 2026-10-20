package com.grocerymate.repository;

import com.grocerymate.entity.Vendors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorsRepository extends JpaRepository<Vendors, Long> {
}
