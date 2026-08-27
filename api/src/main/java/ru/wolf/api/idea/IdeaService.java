/*
 * WOLF — Wolf's Own Life Framework
 * Copyright (C) 2025 Pavel Obukhov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package ru.wolf.api.idea;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.idea.dto.CreateIdeaRequest;
import ru.wolf.api.idea.dto.IdeaResponse;
import ru.wolf.api.idea.dto.PromoteIdeaRequest;
import ru.wolf.api.idea.dto.PromoteResponse;
import ru.wolf.api.idea.dto.UpdateIdeaRequest;
import ru.wolf.api.lifearea.LifeArea;
import ru.wolf.api.lifearea.LifeAreaRepository;
import ru.wolf.api.lifesphere.Synergy;
import ru.wolf.api.lifesphere.SynergyRepository;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IdeaService {

    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final LifeAreaRepository lifeAreaRepository;
    private final ProjectRepository projectRepository;
    private final SynergyRepository synergyRepository;

    @Transactional(readOnly = true)
    public List<IdeaResponse> list(String username, Idea.Category category, Idea.Status status) {
        User user = currentUser(username);
        return ideaRepository.findForUser(user, category, status).stream()
                .map(IdeaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IdeaResponse get(String username, Long id) {
        return IdeaResponse.from(findIdea(currentUser(username), id));
    }

    @Transactional
    public IdeaResponse create(String username, CreateIdeaRequest request) {
        User user = currentUser(username);
        Idea idea = Idea.builder()
                .user(user)
                .title(request.title().trim())
                .description(normalize(request.description()))
                .category(request.category())
                .status(request.status() == null ? Idea.Status.BANK : request.status())
                .build();
        return IdeaResponse.from(ideaRepository.save(idea));
    }

    @Transactional
    public IdeaResponse update(String username, Long id, UpdateIdeaRequest request) {
        User user = currentUser(username);
        Idea idea = findIdea(user, id);
        idea.setTitle(request.title().trim());
        idea.setDescription(normalize(request.description()));
        idea.setCategory(request.category());
        if (idea.getPromotedProject() != null
                && request.status() != null
                && request.status() != idea.getStatus()) {
            throw new IllegalArgumentException("Статус идеи, взятой в работу, меняется только через её жизненный цикл");
        }
        if (request.status() != null) {
            idea.setStatus(request.status());
        }
        return IdeaResponse.from(ideaRepository.save(idea));
    }

    @Transactional
    public void delete(String username, Long id) {
        User user = currentUser(username);
        Idea idea = findIdea(user, id);
        ideaRepository.delete(idea);
    }

    @Transactional
    public PromoteResponse promote(String username, Long id, PromoteIdeaRequest request) {
        User user = currentUser(username);
        Idea idea = ideaRepository.findByUserAndIdForUpdate(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Идея не найдена"));
        if (idea.getPromotedProject() != null || idea.getStatus() == Idea.Status.IN_WORK) {
            throw new IdeaAlreadyPromotedException();
        }
        if (idea.getStatus() != Idea.Status.BANK) {
            throw new IllegalArgumentException("В работу можно взять только идею из банка");
        }

        LifeArea lifeArea = lifeAreaRepository.findByUserAndId(user, request.lifeAreaId())
                .orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена"));
        Project project = projectRepository.save(Project.builder()
                .user(user)
                .lifeArea(lifeArea)
                .title(idea.getTitle())
                .description(idea.getDescription())
                .build());

        List<Synergy> sourceSynergies = synergyRepository.findByUserAndIdeaIdWithSphere(user, idea.getId());
        for (Synergy source : sourceSynergies) {
            synergyRepository.save(Synergy.builder()
                    .user(user)
                    .project(project)
                    .sphere(source.getSphere())
                    .impact(source.getImpact())
                    .build());
        }

        idea.setPromotedProject(project);
        idea.setStatus(Idea.Status.IN_WORK);
        ideaRepository.save(idea);
        return new PromoteResponse(project.getId(), idea.getId());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private Idea findIdea(User user, Long id) {
        return ideaRepository.findByUserAndId(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Идея не найдена"));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
