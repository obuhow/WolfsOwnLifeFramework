package ru.wolf.api.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

@RestController
@RequestMapping("/api/v1/admin/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentJob agentJob;
    private final UserRepository userRepository;

    @PostMapping("/run")
    public ResponseEntity<RunResponse> run(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        AgentJob.AgentRunResult result = agentJob.runForUser(user);
        return ResponseEntity.ok(new RunResponse(
                result.projectsProcessed(), result.notesCreated(), result.runsLogged()));
    }

    public record RunResponse(int projectsProcessed, int notesCreated, int runsLogged) {
    }
}
