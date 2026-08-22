package ru.wolf.api.importxlsx;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.user.User;

@Entity
@Table(name = "activity_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(name = "activity_text", nullable = false, length = 500) private String activityText;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "delo_id") private Delo delo;
}

