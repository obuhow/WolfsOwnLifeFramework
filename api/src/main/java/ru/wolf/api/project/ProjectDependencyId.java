package ru.wolf.api.project;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProjectDependencyId implements Serializable {
    private Long blocker;
    private Long blocked;
}
