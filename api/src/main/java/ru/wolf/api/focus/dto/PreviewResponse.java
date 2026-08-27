package ru.wolf.api.focus.dto;
import java.util.List;
import ru.wolf.api.focus.dto.Change;
import ru.wolf.api.focus.dto.Occupied;



public record PreviewResponse(List<Change> changes, List<Occupied> occupied) {}
