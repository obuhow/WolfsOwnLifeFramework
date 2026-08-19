package ru.wolf.api.goal;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.project.Project;

@Entity
@Table(name = "goal_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalProject {

    @EmbeddedId
    private GoalProjectId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("goalId")
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
