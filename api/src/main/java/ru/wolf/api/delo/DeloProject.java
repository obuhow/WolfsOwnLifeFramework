package ru.wolf.api.delo;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.project.Project;

@Entity
@Table(name = "delo_project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeloProject {

    @EmbeddedId
    private DeloProjectId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deloId")
    @JoinColumn(name = "delo_id", nullable = false)
    private Delo delo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;
}