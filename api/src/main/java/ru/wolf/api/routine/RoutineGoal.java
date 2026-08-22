package ru.wolf.api.routine;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.goal.Goal;

@Entity
@Table(name = "routine_goal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutineGoal {

    @EmbeddedId
    private RoutineGoalId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("routineId")
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("goalId")
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;
}
