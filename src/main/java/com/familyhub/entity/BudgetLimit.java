package com.familyhub.entity;

import com.familyhub.entity.enums.SpendingCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "budget_limits",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_budget_limit",
                columnNames = {"family_id", "category"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "family")
public class BudgetLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Budget limits are always family-scoped — only PARENT can create or edit them
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SpendingCategory category;

    // Monthly spending ceiling for this category in the family's local currency
    @Column(name = "monthly_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyLimit;
}
