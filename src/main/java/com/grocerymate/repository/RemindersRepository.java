package com.grocerymate.repository;

import com.grocerymate.entity.Reminders;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemindersRepository extends JpaRepository<Reminders, Long> {
}
