package ru.wolf.api.datasync;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

public interface SyncExternalIdRepository extends JpaRepository<SyncExternalId, Long> {
    Optional<SyncExternalId> findByUserAndEntityTypeAndEntityId(User user, String entityType, Long entityId);
    Optional<SyncExternalId> findByUserAndEntityTypeAndExternalId(User user, String entityType, String externalId);
    List<SyncExternalId> findByUserAndEntityType(User user, String entityType);

    /**
     * Соответствия «внешний id → сущность» указывают на удаляемые сущности профиля
     * без внешнего ключа: после очистки они стали бы битыми ссылками.
     * См. {@code UserPurgeService}.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from SyncExternalId s where s.user = :user")
    void deleteAllByUser(@org.springframework.data.repository.query.Param("user") User user);

}
