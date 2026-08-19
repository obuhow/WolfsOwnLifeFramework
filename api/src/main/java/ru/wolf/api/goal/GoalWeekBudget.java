package ru.wolf.api.goal;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "goal_week_budget", uniqueConstraints = @UniqueConstraint(name = "uq_goal_budget_week", columnNames = {"goal_id", "iso_year", "iso_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalWeekBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(name = "iso_year", nullable = false)
    private Integer isoYear;

    @Column(name = "iso_week", nullable = false)
    private Integer isoWeek;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hours;
}
