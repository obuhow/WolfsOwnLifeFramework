package ru.wolf.api.datasync;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;

import java.util.Optional;

public interface SyncImportPreviewRepository extends JpaRepository<SyncImportPreview, Long> {
    Optional<SyncImportPreview> findByIdAndUser(Long id, User user);
}
