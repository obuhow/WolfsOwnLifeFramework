package ru.wolf.api.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;

@Repository
public interface AgentRunLogRepository extends JpaRepository<AgentRunLog, Long> {
    List<AgentRunLog> findByUserOrderByStartedAtDesc(User user);
}
