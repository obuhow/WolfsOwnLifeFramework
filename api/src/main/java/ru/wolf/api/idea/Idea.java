package ru.wolf.api.idea;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import ru.wolf.api.project.Project;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "idea")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Idea {

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
    @Column(nullable = false, length = 20)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.BANK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_project_id")
    private Project promotedProject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Category {
        BUSINESS,
        MUSIC,
        PERSONAL,
        CREEPY
    }

    public enum Status {
        BANK,
        IN_WORK,
        ARCHIVED
    }
}
