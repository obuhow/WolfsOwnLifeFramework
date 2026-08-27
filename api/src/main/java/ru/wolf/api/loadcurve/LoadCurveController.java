package ru.wolf.api.loadcurve;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.loadcurve.dto.LoadCurveRequest;
import ru.wolf.api.loadcurve.dto.LoadCurveResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LoadCurveController {
    private final LoadCurveService service;

    @GetMapping("/api/v1/projects/{id}/load-curve")
    public List<LoadCurveResponse> project(Authentication authentication, @PathVariable Long id) {
        return service.project(authentication.getName(), id);
    }

    @GetMapping("/api/v1/routines/{id}/load-curve")
    public List<LoadCurveResponse> routine(Authentication authentication, @PathVariable Long id) {
        return service.routine(authentication.getName(), id);
    }

    @PutMapping("/api/v1/projects/{id}/load-curve")
    public LoadCurveResponse putProject(Authentication authentication, @PathVariable Long id,
                                        @Valid @RequestBody LoadCurveRequest request) {
        return service.putProject(authentication.getName(), id, request);
    }

    @PutMapping("/api/v1/routines/{id}/load-curve")
    public LoadCurveResponse putRoutine(Authentication authentication, @PathVariable Long id,
                                        @Valid @RequestBody LoadCurveRequest request) {
        return service.putRoutine(authentication.getName(), id, request);
    }

    @DeleteMapping("/api/v1/projects/{id}/load-curve/{entryId}")
    public ResponseEntity<Void> deleteProject(Authentication authentication, @PathVariable Long id,
                                              @PathVariable Long entryId) {
        service.deleteProject(authentication.getName(), id, entryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/routines/{id}/load-curve/{entryId}")
    public ResponseEntity<Void> deleteRoutine(Authentication authentication, @PathVariable Long id,
                                              @PathVariable Long entryId) {
        service.deleteRoutine(authentication.getName(), id, entryId);
        return ResponseEntity.noContent().build();
    }
}
