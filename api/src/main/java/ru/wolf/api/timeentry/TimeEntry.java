package ru.wolf.api.timeentry;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Запись времени — continuous interval [startAt, endAt) on the grid (multiples of 15 min).
 * Empty cells are absence of a covering interval (status «неопределено»).
 */
@Entity
@Table(name = "time_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delo_id")
    private Delo delo;

    @Column(name = "ad_hoc_text", length = 500)
    private String adHocText;

    /** Inclusive start of the interval, wall-clock in user timezone (aligned to 15 min). */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** Exclusive end of the interval (aligned to 15 min), must be after startAt. */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Status {
        /** запланирована */
        PLANNED,
        /** выполнена */
        DONE
    }

    public boolean isAdHoc() {
        return delo == null && adHocText != null && !adHocText.isBlank();
    }
}
