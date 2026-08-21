package ru.wolf.api.datasync;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;

import java.util.Optional;

public interface SyncExternalIdRepository extends JpaRepository<SyncExternalId, Long> {
    Optional<SyncExternalId> findByUserAndEntityTypeAndEntityId(User user, String entityType, Long entityId);
    Optional<SyncExternalId> findByUserAndEntityTypeAndExternalId(User user, String entityType, String externalId);
}
