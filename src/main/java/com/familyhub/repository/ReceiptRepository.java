package com.familyhub.repository;

import com.familyhub.entity.Receipt;
import com.familyhub.entity.enums.ReceiptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    // All receipts for a family — newest first, with uploadedBy and items preloaded for index previews.
    @EntityGraph(attributePaths = {"uploadedBy", "items"})
    List<Receipt> findAllByFamilyIdOrderByCreatedAtDesc(Long familyId);

    // Used for spending statistics — only DONE receipts have valid extracted data
    List<Receipt> findByFamilyIdAndStatus(Long familyId, ReceiptStatus status);

    // Security check — ensure the receipt belongs to the user's family before loading full detail.
    @EntityGraph(attributePaths = {"uploadedBy", "items"})
    Optional<Receipt> findByIdAndFamilyId(Long id, Long familyId);
}
