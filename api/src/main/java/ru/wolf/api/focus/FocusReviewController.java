package ru.wolf.api.focus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wolf.api.focus.dto.AllocationRequest;
import ru.wolf.api.focus.dto.PreviewResponse;
import ru.wolf.api.focus.dto.ReviewResponse;
@RestController @RequestMapping("/api/v1/focus") @RequiredArgsConstructor
public class FocusReviewController {
    private final FocusReviewService service;
 @GetMapping("/{id}/review") public ReviewResponse review(Authentication a,@PathVariable Long id){return service.review(a.getName(),id);}
 @PostMapping("/{id}/review/preview") public PreviewResponse preview(Authentication a,@PathVariable Long id,@Valid @RequestBody AllocationRequest r){return service.preview(a.getName(),id,r);}
 @PostMapping("/{id}/review/apply") public PreviewResponse apply(Authentication a,@PathVariable Long id,@Valid @RequestBody AllocationRequest r){return service.apply(a.getName(),id,r);}
 @PostMapping("/{id}/review/revert") public PreviewResponse revert(Authentication a,@PathVariable Long id){return service.revert(a.getName(),id);}
}
