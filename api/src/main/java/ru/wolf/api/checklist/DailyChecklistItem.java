package ru.wolf.api.checklist;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;
import java.time.*;

@Entity
@Table(name = "daily_checklist_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private LocalDate date;
    @Column(nullable = false, length = 500) private String title;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delo_id") private Delo delo;
    @Column(nullable = false) private Integer position;
    @Column(nullable = false) private boolean done;
    @Column(name = "done_at") private Instant doneAt;
    @PrePersist void init() { if (position == null) position = 0; }
}
