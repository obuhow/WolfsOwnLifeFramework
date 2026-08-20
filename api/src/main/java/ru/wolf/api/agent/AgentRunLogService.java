package ru.wolf.api.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentRunLogService {

    private final AgentRunLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRunLog save(AgentRunLog log) {
        return repository.save(log);
    }
}
