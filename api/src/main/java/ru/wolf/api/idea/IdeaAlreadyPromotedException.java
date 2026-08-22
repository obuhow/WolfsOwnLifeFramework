package ru.wolf.api.idea;

public class IdeaAlreadyPromotedException extends RuntimeException {

    public IdeaAlreadyPromotedException() {
        super("Идея уже взята в работу");
    }
}
