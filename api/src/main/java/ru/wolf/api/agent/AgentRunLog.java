package ru.wolf.api.agent;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "agent_run_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "projects_processed", nullable = false)
    @Builder.Default
    private int projectsProcessed = 0;

    @Column(name = "notes_created", nullable = false)
    @Builder.Default
    private int notesCreated = 0;

    @Column(columnDefinition = "TEXT")
    private String error;
}

// A log row is deliberately scoped to a user: scheduled runs never mix personal spaces.
