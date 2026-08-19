package ru.wolf.api.lifesphere;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "synergy", uniqueConstraints = {
        @UniqueConstraint(name = "uk_synergy_project_sphere", columnNames = {"project_id", "sphere_id"}),
        @UniqueConstraint(name = "uk_synergy_idea_sphere", columnNames = {"idea_id", "sphere_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Synergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ru.wolf.api.user.User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ru.wolf.api.project.Project project;

    @Column(name = "idea_id")
    private Long ideaId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sphere_id", nullable = false)
    private LifeSphere sphere;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 10)
    private Impact impact;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Impact {
        POSITIVE,   // +1
        NEGATIVE,   // -1
        NEUTRAL     // 0
    }
}
