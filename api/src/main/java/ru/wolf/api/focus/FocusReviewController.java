package ru.wolf.api.focus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.focus.dto.*;
@RestController @RequestMapping("/api/v1/focus") @RequiredArgsConstructor
public class FocusReviewController {
 private final FocusReviewService service;
 @GetMapping("/{id}/review") public ReviewResponse review(Authentication a,@PathVariable Long id){return service.review(a,id);}
 @PostMapping("/{id}/review/preview") public PreviewResponse preview(Authentication a,@PathVariable Long id,@Valid @RequestBody AllocationRequest r){return service.preview(a,id,r);}
 @PostMapping("/{id}/review/apply") public PreviewResponse apply(Authentication a,@PathVariable Long id,@Valid @RequestBody AllocationRequest r){return service.apply(a,id,r);}
 @PostMapping("/{id}/review/revert") public PreviewResponse revert(Authentication a,@PathVariable Long id){return service.revert(a,id);}
}
