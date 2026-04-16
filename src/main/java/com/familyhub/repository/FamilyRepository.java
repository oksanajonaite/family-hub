package com.familyhub.repository;

import com.familyhub.entity.Family;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {

    // @EntityGraph forces a LEFT JOIN FETCH on createdBy — avoids N+1 when the admin template
    // accesses family.createdBy.displayName for every row in the families table
    @EntityGraph(attributePaths = "createdBy")
    List<Family> findAllByOrderByCreatedAtAsc();
}
