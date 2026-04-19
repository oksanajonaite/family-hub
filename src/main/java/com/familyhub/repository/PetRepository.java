package com.familyhub.repository;

import com.familyhub.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findAllByFamilyId(Long familyId);

    // Used when deleting an entire family — removes all pets for the family
    void deleteAllByFamilyId(Long familyId);
}
