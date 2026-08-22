package ru.wolf.api.loadcurve;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.routine.Routine;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "load_curve_entry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoadCurveEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id") private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "routine_id") private Routine routine;
    @Column(name = "week_start", nullable = false) private LocalDate weekStart;
    @Column(nullable = false, precision = 8, scale = 2) private BigDecimal hours;
}

