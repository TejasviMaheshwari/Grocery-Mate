package com.grocerymate.repository;

import com.grocerymate.entity.Households;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdsRepository extends JpaRepository<Households, Long> {
}
