package com.grocerymate.repository;

import com.grocerymate.entity.HouseholdItems;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdItemsRepository extends JpaRepository<HouseholdItems, Long> {
}
