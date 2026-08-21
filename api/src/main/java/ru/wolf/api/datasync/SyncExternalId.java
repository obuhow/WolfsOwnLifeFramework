package ru.wolf.api.datasync;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

@Entity
@Table(name = "sync_external_id", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sync_external_id_value", columnNames = {"user_id", "entity_type", "external_id"}),
        @UniqueConstraint(name = "uk_sync_external_id_entity", columnNames = {"user_id", "entity_type", "entity_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SyncExternalId {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;
}
