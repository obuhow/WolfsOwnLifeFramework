package ru.wolf.api.agentchat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.wolf.api.user.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentActionProposalRepository extends JpaRepository<AgentActionProposal, Long> {

    List<AgentActionProposal> findByUserAndSessionIdOrderByIdAsc(User user, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM AgentActionProposal p
            WHERE p.user = :user AND p.session.id = :sessionId AND p.id = :id
            """)
    Optional<AgentActionProposal> findForUpdate(
            @Param("user") User user,
            @Param("sessionId") Long sessionId,
            @Param("id") Long id);
}
