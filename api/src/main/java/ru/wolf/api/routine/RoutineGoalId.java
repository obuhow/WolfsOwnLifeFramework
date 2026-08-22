package ru.wolf.api.routine;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoutineGoalId implements Serializable {
    private Long routineId;
    private Long goalId;
}
