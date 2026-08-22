package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;
import java.util.Optional;

public interface XlsxImportRunRepository extends JpaRepository<XlsxImportRun, Long> {
    Optional<XlsxImportRun> findByUserAndFileHash(User user, String fileHash);
    
    @Query("SELECT r FROM XlsxImportRun r WHERE r.user = :user AND r.id = :id")
    Optional<XlsxImportRun> findByUserAndId(@Param("user") User user, @Param("id") Long id);
}
