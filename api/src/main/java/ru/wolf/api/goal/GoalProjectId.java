package ru.wolf.api.goal;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class GoalProjectId implements Serializable {
    private Long goalId;
    private Long projectId;
}
