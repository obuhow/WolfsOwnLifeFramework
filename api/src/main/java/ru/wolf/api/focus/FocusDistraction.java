package ru.wolf.api.focus;
import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import java.time.LocalDateTime;
@Entity @Table(name="focus_distraction") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FocusDistraction { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="focus_session_id") private FocusSession session; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="delo_id") private Delo delo; private String text; @Column(name="at",nullable=false) private LocalDateTime at; private Integer minutes; @Column(name="applied_at") private LocalDateTime appliedAt; @Column(name="applied_minutes") private Integer appliedMinutes; }
