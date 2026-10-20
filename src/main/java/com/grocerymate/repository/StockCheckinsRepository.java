package com.grocerymate.repository;

import com.grocerymate.entity.StockCheckins;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockCheckinsRepository extends JpaRepository<StockCheckins, Long> {
}
