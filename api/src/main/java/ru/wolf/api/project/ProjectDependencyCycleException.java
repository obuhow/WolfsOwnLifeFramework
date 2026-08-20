package ru.wolf.api.project;

import java.util.List;

public class ProjectDependencyCycleException extends RuntimeException {

    public ProjectDependencyCycleException(List<String> cyclePath) {
        super("Цикл зависимостей: " + String.join(" → ", cyclePath));
    }
}
