package com.familyhub.repository;

import com.familyhub.entity.BudgetLimit;
import com.familyhub.entity.enums.SpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetLimitRepository extends JpaRepository<BudgetLimit, Long> {

    // All limits set by a family — displayed on the budget settings page
    List<BudgetLimit> findByFamilyId(Long familyId);

    // Used when checking whether spending has hit 80% or 100% of a limit
    Optional<BudgetLimit> findByFamilyIdAndCategory(Long familyId, SpendingCategory category);
}
