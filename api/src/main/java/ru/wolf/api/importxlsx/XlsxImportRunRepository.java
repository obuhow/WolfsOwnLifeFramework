package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;
import java.util.Optional;

public interface XlsxImportRunRepository extends JpaRepository<XlsxImportRun, Long> {
    Optional<XlsxImportRun> findByUserAndFileHash(User user, String fileHash);
}
