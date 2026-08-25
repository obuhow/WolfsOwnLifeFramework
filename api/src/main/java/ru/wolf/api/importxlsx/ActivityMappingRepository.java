package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.wolf.api.user.User;

import java.util.Optional;

public interface ActivityMappingRepository extends JpaRepository<ActivityMapping, Long> {
    Optional<ActivityMapping> findByUserAndActivityText(User user, String activityText);

    /**
     * Соответствия «текст активности → Дело» ссылаются на Дела профиля;
     * без их удаления очистка упала бы на внешнем ключе. См. {@code UserPurgeService}.
     */
    @Modifying
    @Query("delete from ActivityMapping m where m.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
