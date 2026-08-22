package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wolf.api.user.User;

import java.util.Optional;

public interface ActivityMappingRepository extends JpaRepository<ActivityMapping, Long> {
    Optional<ActivityMapping> findByUserAndActivityText(User user, String activityText);
}
