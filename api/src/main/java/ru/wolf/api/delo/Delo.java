package ru.wolf.api.delo;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.wolf.api.user.User;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "delo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    @Builder.Default
    private ExecutionMode executionMode = ExecutionMode.SELF;

    /** Comma-separated {@link java.time.DayOfWeek} names, e.g. MONDAY,WEDNESDAY. */
    @Column(name = "recurrence_weekdays", length = 64)
    private String recurrenceWeekdays;

    @Column(name = "recurrence_window_start")
    private LocalTime recurrenceWindowStart;

    @Column(name = "recurrence_window_end")
    private LocalTime recurrenceWindowEnd;

    /** JSON list of {weekday, windowStart, windowEnd}. */
    @Column(name = "recurrence_slots", columnDefinition = "TEXT")
    private String recurrenceSlots;

    @OneToMany(mappedBy = "delo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<DeloProject> deloProjects = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ExecutionMode {
        SELF,
        DELEGATABLE,
        AUTOMATABLE
    }
}