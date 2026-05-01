package com.familyhub.repository;

import com.familyhub.entity.ReceiptItem;
import com.familyhub.entity.enums.SpendingCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReceiptItemRepository extends JpaRepository<ReceiptItem, Long> {

    // All items for a given receipt
    List<ReceiptItem> findByReceiptId(Long receiptId);

    // Spending total per category for a family in a given month — used on the statistics page.
    // Joins through receipt so we can filter by family and only count DONE receipts.
    @Query("""
            SELECT ri.category, SUM(ri.unitPrice * ri.quantity)
            FROM ReceiptItem ri
            JOIN ri.receipt r
            WHERE r.family.id = :familyId
              AND r.status = com.familyhub.entity.enums.ReceiptStatus.DONE
              AND r.purchaseDate >= :from
              AND r.purchaseDate <= :to
            GROUP BY ri.category
            """)
    List<Object[]> sumByCategory(
            @Param("familyId") Long familyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // Daily totals for a family in a given date range — used to build weekly spending breakdown.
    @Query("""
            SELECT r.purchaseDate, SUM(ri.unitPrice * ri.quantity)
            FROM ReceiptItem ri
            JOIN ri.receipt r
            WHERE r.family.id = :familyId
              AND r.status = com.familyhub.entity.enums.ReceiptStatus.DONE
              AND r.purchaseDate >= :from
              AND r.purchaseDate <= :to
            GROUP BY r.purchaseDate
            ORDER BY r.purchaseDate
            """)
    List<Object[]> sumByDate(
            @Param("familyId") Long familyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // Monthly totals grouped by year and month — used for the bar chart without one query per month.
    @Query("""
            SELECT YEAR(r.purchaseDate), MONTH(r.purchaseDate), SUM(ri.unitPrice * ri.quantity)
            FROM ReceiptItem ri
            JOIN ri.receipt r
            WHERE r.family.id = :familyId
              AND r.status = com.familyhub.entity.enums.ReceiptStatus.DONE
              AND r.purchaseDate >= :from
              AND r.purchaseDate <= :to
            GROUP BY YEAR(r.purchaseDate), MONTH(r.purchaseDate)
            ORDER BY YEAR(r.purchaseDate), MONTH(r.purchaseDate)
            """)
    List<Object[]> sumMonthlyTotals(
            @Param("familyId") Long familyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
