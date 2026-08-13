package ru.wolf.api.backlog;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

import java.time.Instant;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "week_backlog", uniqueConstraints = {
        @UniqueConstraint(name = "uk_week_backlog_user_week", columnNames = {"user_id", "iso_year", "iso_week"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeekBacklog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "iso_year", nullable = false)
    private Integer isoYear;

    @Column(name = "iso_week", nullable = false)
    private Integer isoWeek;

    @ManyToMany
    @JoinTable(
            name = "week_backlog_delo",
            joinColumns = @JoinColumn(name = "backlog_id"),
            inverseJoinColumns = @JoinColumn(name = "delo_id")
    )
    @Builder.Default
    private Set<Delo> delos = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addDelo(Delo delo) {
        this.delos.add(delo);
    }

    public void removeDelo(Delo delo) {
        this.delos.remove(delo);
    }
}