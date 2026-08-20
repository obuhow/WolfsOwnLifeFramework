package ru.wolf.api.focus;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;
import java.time.LocalDateTime;

@Entity
@Table(name = "focus_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FocusSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delo_id", nullable = false)
    private Delo delo;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    private String note;
}
