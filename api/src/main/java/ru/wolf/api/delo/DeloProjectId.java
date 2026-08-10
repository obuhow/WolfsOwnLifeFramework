package ru.wolf.api.delo;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeloProjectId implements Serializable {

    private Long deloId;
    private Long projectId;
}