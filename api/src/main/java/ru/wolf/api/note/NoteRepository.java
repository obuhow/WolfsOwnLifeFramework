package ru.wolf.api.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    @Query(value = """
            SELECT n.* FROM note n
            WHERE n.user_id = :#{#user.id}
              AND (:projectId IS NULL OR n.project_id = :projectId)
              AND (:deloId IS NULL OR n.delo_id = :deloId)
              AND (:author IS NULL OR n.author = :author)
              AND (:tag IS NULL OR EXISTS (
                    SELECT 1 FROM unnest(n.tags) AS tag_value
                    WHERE LOWER(tag_value) = LOWER(:tag)
              ))
              AND (:q IS NULL OR to_tsvector('simple', n.body) @@ plainto_tsquery('simple', :q))
            ORDER BY n.created_at DESC, n.id DESC
            """, nativeQuery = true)
    List<Note> search(
            @Param("user") User user,
            @Param("projectId") Long projectId,
            @Param("deloId") Long deloId,
            @Param("author") String author,
            @Param("tag") String tag,
            @Param("q") String query
    );

    @Query("""
            SELECT n FROM Note n
            LEFT JOIN FETCH n.project
            LEFT JOIN FETCH n.delo
            WHERE n.user = :user AND n.id = :id
            """)
    Optional<Note> findByUserAndId(@Param("user") User user, @Param("id") Long id);

    @Query("""
            SELECT n FROM Note n
            WHERE n.user = :user AND n.project.id = :projectId
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    List<Note> findByUserAndProjectIdOrderByCreatedAtDesc(
            @Param("user") User user,
            @Param("projectId") Long projectId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM note n
                WHERE n.user_id = :#{#user.id}
                  AND n.project_id = :projectId
                  AND n.author = :author
                  AND :tag = ANY(n.tags)
                  AND n.created_at >= :from
                  AND n.created_at < :to
            )
            """, nativeQuery = true)
    boolean existsByUserAndProjectIdAndAuthorAndTagAndCreatedAtBetween(
            @Param("user") User user,
            @Param("projectId") Long projectId,
            @Param("author") String author,
            @Param("tag") String tag,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
