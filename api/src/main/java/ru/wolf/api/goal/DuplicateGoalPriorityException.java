package ru.wolf.api.goal;

public class DuplicateGoalPriorityException extends RuntimeException {
    public DuplicateGoalPriorityException(int priority) {
        super("Приоритет уже занят: " + priority);
    }
}
