package ru.wolf.api.agentchat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.agentchat.dto.AgentActionApplyResponse;
import ru.wolf.api.agentchat.dto.ProposedActionResponse;
import ru.wolf.api.delo.Delo;
import ru.wolf.api.delo.DeloService;
import ru.wolf.api.delo.dto.ApplyRecurrenceRequest;
import ru.wolf.api.delo.dto.CreateDeloRequest;
import ru.wolf.api.delo.dto.DeloResponse;
import ru.wolf.api.delo.dto.UpdateDeloRequest;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectService;
import ru.wolf.api.project.dto.CreateProjectRequest;
import ru.wolf.api.project.dto.ProjectResponse;
import ru.wolf.api.project.dto.UpdateProjectRequest;
import ru.wolf.api.timeentry.TimeEntry;
import ru.wolf.api.timeentry.TimeEntryService;
import ru.wolf.api.timeentry.dto.PutTimeEntryRequest;
import ru.wolf.api.timeentry.dto.TimeEntryResponse;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentActionService {

    private static final int MAX_PROPOSAL_AGE_MINUTES = 30;

    private final UserRepository userRepository;
    private final AgentActionProposalRepository proposalRepository;
    private final ObjectMapper objectMapper;
    private final DeloService deloService;
    private final ProjectService projectService;
    private final TimeEntryService timeEntryService;

    public ProposedActionResponse createProposal(User user, ChatSession session,
                                                 ChatMessage assistantMessage, AgentAction action) {
        try {
            AgentActionProposal proposal = proposalRepository.save(AgentActionProposal.builder()
                    .user(user)
                    .session(session)
                    .assistantMessage(assistantMessage)
                    .actionType(action.type())
                    .targetId(action.targetId())
                    .fieldsJson(objectMapper.writeValueAsString(action.fields()))
                    .build());
            return toResponse(proposal, action.fields());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Предлагаемое действие агента имеет некорректные поля", ex);
        }
    }

    @Transactional
    public AgentActionApplyResponse apply(String username, Long sessionId, Long proposalId) {
        User user = currentUser(username);
        AgentActionProposal proposal = proposalRepository.findForUpdate(user, sessionId, proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Предлагаемое действие не найдено"));

        if (proposal.getStatus() == AgentActionProposal.Status.APPLIED) {
            return new AgentActionApplyResponse(proposal.getId(), proposal.getStatus(), false,
                    readResult(proposal.getResultJson()));
        }
        if (proposal.getStatus() != AgentActionProposal.Status.PENDING) {
            throw new IllegalArgumentException("Предлагаемое действие уже нельзя применить: " + proposal.getStatus());
        }
        if (proposal.getCreatedAt().plusSeconds(MAX_PROPOSAL_AGE_MINUTES * 60L).isBefore(Instant.now())) {
            proposal.setStatus(AgentActionProposal.Status.EXPIRED);
            proposalRepository.save(proposal);
            return new AgentActionApplyResponse(proposal.getId(), proposal.getStatus(), false, Map.of());
        }

        Map<String, Object> fields = readFields(proposal.getFieldsJson());
        Map<String, Object> result = execute(user, proposal.getActionType(), proposal.getTargetId(), fields);
        try {
            proposal.setResultJson(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сохранить результат действия", ex);
        }
        proposal.setStatus(AgentActionProposal.Status.APPLIED);
        proposal.setAppliedAt(Instant.now());
        proposalRepository.save(proposal);
        return new AgentActionApplyResponse(proposal.getId(), proposal.getStatus(), true, result);
    }

    @Transactional
    public AgentActionApplyResponse reject(String username, Long sessionId, Long proposalId) {
        AgentActionProposal proposal = proposalRepository.findForUpdate(currentUser(username), sessionId, proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Предлагаемое действие не найдено"));
        if (proposal.getStatus() == AgentActionProposal.Status.PENDING) {
            proposal.setStatus(AgentActionProposal.Status.REJECTED);
            proposalRepository.save(proposal);
        }
        return new AgentActionApplyResponse(proposal.getId(), proposal.getStatus(), false,
                readResult(proposal.getResultJson()));
    }

    private Map<String, Object> execute(User user, AgentAction.Type type, Long targetId,
                                        Map<String, Object> fields) {
        String username = user.getUsername();
        return switch (type) {
            case CREATE_DELO -> result(deloService.createDelo(username, new CreateDeloRequest(
                    requiredText(fields, "title"), text(fields, "description"),
                    enumValue(Delo.ExecutionMode.class, fields, "executionMode", Delo.ExecutionMode.SELF),
                    longList(fields, "projectIds"), optionalLong(fields, "primaryProjectId"),
                    booleanValue(fields, "supporting"))));
            case UPDATE_DELO -> result(deloService.updateDelo(username, requiredTarget(targetId), new UpdateDeloRequest(
                    requiredText(fields, "title"), text(fields, "description"),
                    enumValue(Delo.ExecutionMode.class, fields, "executionMode", Delo.ExecutionMode.SELF),
                    longList(fields, "projectIds"), optionalLong(fields, "primaryProjectId"),
                    booleanValue(fields, "supporting"))));
            case DELETE_DELO -> {
                deloService.deleteDelo(username, requiredTarget(targetId));
                yield simpleResult("deleted", requiredTarget(targetId));
            }
            case CREATE_PROJECT -> result(projectService.createProject(username, new CreateProjectRequest(
                    requiredLong(fields, "lifeAreaId"), optionalLong(fields, "parentId"), requiredText(fields, "title"),
                    enumValue(Project.Status.class, fields, "status", Project.Status.IN_PROGRESS),
                    text(fields, "description"), localDate(fields, "startDate"), localDate(fields, "endDate"),
                    decimal(fields, "totalPlanHours"), enumValue(Project.PlanDistribution.class, fields,
                            "planDistribution", Project.PlanDistribution.NONE))));
            case UPDATE_PROJECT -> result(projectService.updateProject(username, requiredTarget(targetId), new UpdateProjectRequest(
                    requiredLong(fields, "lifeAreaId"), optionalLong(fields, "parentId"), requiredText(fields, "title"),
                    enumValue(Project.Status.class, fields, "status", Project.Status.IN_PROGRESS),
                    text(fields, "description"), localDate(fields, "startDate"), localDate(fields, "endDate"),
                    decimal(fields, "totalPlanHours"), enumValue(Project.PlanDistribution.class, fields,
                            "planDistribution", Project.PlanDistribution.NONE))));
            case DELETE_PROJECT -> {
                projectService.deleteProject(username, requiredTarget(targetId));
                yield simpleResult("deleted", requiredTarget(targetId));
            }
            case CREATE_TIME_ENTRY -> result(body(timeEntryService.putEntry(username, new PutTimeEntryRequest(
                    requiredText(fields, "startAt"), text(fields, "endAt"), optionalLong(fields, "deloId"),
                    text(fields, "adHocText"), enumValue(TimeEntry.Status.class, fields, "status", null)))));
            case UPDATE_TIME_ENTRY -> result(body(timeEntryService.updateEntry(username, requiredTarget(targetId),
                    new PutTimeEntryRequest(requiredText(fields, "startAt"), text(fields, "endAt"),
                            optionalLong(fields, "deloId"), text(fields, "adHocText"),
                            enumValue(TimeEntry.Status.class, fields, "status", null)))));
            case DELETE_TIME_ENTRY -> {
                timeEntryService.deleteEntry(username, requiredTarget(targetId));
                yield simpleResult("deleted", requiredTarget(targetId));
            }
            case APPLY_RECURRENCE -> result(body(deloService.applyRecurrence(username, requiredTarget(targetId),
                    new ApplyRecurrenceRequest(
                            dayList(fields, "weekdays"), localTime(fields, "windowStart"), localTime(fields, "windowEnd"),
                            integer(fields, "horizonWeeks"), List.of()))));
        };
    }

    private ProposedActionResponse toResponse(AgentActionProposal proposal, Map<String, Object> fields) {
        return new ProposedActionResponse(proposal.getId(), proposal.getActionType(), proposal.getTargetId(),
                fields, proposal.getStatus());
    }

    private Map<String, Object> readFields(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Предложение агента повреждено", ex);
        }
    }

    private Map<String, Object> readResult(String json) {
        return json == null ? Map.of() : readFields(json);
    }

    private <T> Map<String, Object> result(T value) {
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    private Map<String, Object> body(Object value) {
        return result(value);
    }

    private Map<String, Object> simpleResult(String key, Long value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private String requiredText(Map<String, Object> fields, String name) {
        String value = text(fields, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Поле действия обязательно: " + name);
        return value.trim();
    }

    private String text(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private Long requiredTarget(Long targetId) {
        if (targetId == null) throw new IllegalArgumentException("Действию нужен targetId");
        return targetId;
    }

    private Long requiredLong(Map<String, Object> fields, String name) {
        Long value = optionalLong(fields, name);
        if (value == null) throw new IllegalArgumentException("Поле действия обязательно: " + name);
        return value;
    }

    private Long optionalLong(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Поле " + name + " должно быть числом"); }
    }

    private boolean booleanValue(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> fields, String name) {
        String value = text(fields, name);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private LocalDate localDate(Map<String, Object> fields, String name) {
        String value = text(fields, name);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private LocalTime localTime(Map<String, Object> fields, String name) {
        String value = text(fields, name);
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private Integer integer(Map<String, Object> fields, String name) {
        Long value = optionalLong(fields, name);
        return value == null ? null : Math.toIntExact(value);
    }

    private List<Long> longList(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Поле " + name + " должно быть массивом");
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) result.add(number.longValue());
            else result.add(Long.parseLong(String.valueOf(item)));
        }
        return result;
    }

    private List<DayOfWeek> dayList(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Поле " + name + " должно быть массивом");
        return list.stream().map(item -> DayOfWeek.valueOf(String.valueOf(item).toUpperCase())).toList();
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, Map<String, Object> fields, String name, T fallback) {
        String value = text(fields, name);
        return value == null || value.isBlank() ? fallback : Enum.valueOf(type, value.toUpperCase());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
