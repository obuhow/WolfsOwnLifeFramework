package ru.wolf.api.datasync;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.user.User;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncExternalIdService {
    private final SyncExternalIdRepository repository;

    @Transactional
    public String externalId(User user, String entityType, Long entityId) {
        return repository.findByUserAndEntityTypeAndEntityId(user, entityType, entityId)
                .map(SyncExternalId::getExternalId)
                .orElseGet(() -> repository.save(SyncExternalId.builder()
                        .user(user)
                        .entityType(entityType)
                        .entityId(entityId)
                        .externalId(entityType + "-" + UUID.randomUUID())
                        .build()).getExternalId());
    }
}
