package com.grocerymate.repository;

import com.grocerymate.entity.HouseholdMembers;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMembersRepository extends JpaRepository<HouseholdMembers, Long> {
}
