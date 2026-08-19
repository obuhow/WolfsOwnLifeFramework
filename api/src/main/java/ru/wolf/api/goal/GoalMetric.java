package ru.wolf.api.goal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "goal_metric")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(nullable = false, length = 100)
    private String kind;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime at;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
