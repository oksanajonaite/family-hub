package com.familyhub.repository;

import com.familyhub.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findAllByFamilyId(Long familyId); // account-less members managed by PARENT

    // Used when deleting an entire family — removes all account-less members for the family
    void deleteAllByFamilyId(Long familyId);

    // Used by the birthday reminder scheduler — finds account-less members whose birthday falls on the given month/day.
    @Query("SELECT fm FROM FamilyMember fm WHERE fm.dateOfBirth IS NOT NULL " +
           "AND MONTH(fm.dateOfBirth) = :month AND DAY(fm.dateOfBirth) = :day")
    List<FamilyMember> findMembersWithBirthdayOn(@Param("month") int month, @Param("day") int day);
}
