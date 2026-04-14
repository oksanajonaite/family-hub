package com.familyhub.repository;

import com.familyhub.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findAllByFamilyId(Long familyId); // account-less members managed by PARENT
}
