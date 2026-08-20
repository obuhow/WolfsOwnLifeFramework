package ru.wolf.api.checklist;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.wolf.api.delo.*;
import ru.wolf.api.user.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/checklist") @RequiredArgsConstructor
public class ChecklistController {
    private final DailyChecklistItemRepository items; private final UserRepository users; private final DeloRepository delos;
    @GetMapping @Transactional(readOnly=true) public List<Response> list(Authentication a,@RequestParam LocalDate date){return items.findForDate(user(a),date).stream().map(this::response).toList();}
    @PostMapping @Transactional public ResponseEntity<Response> create(Authentication a,@RequestBody Request r){User u=user(a); DailyChecklistItem i=DailyChecklistItem.builder().user(u).date(r.date()).title(r.title().trim()).position(r.position()==null?0:r.position()).done(false).delo(r.deloId()==null?null:delos.findByUserAndId(u,r.deloId()).orElseThrow()).build();return ResponseEntity.status(201).body(response(items.save(i)));}
    @PatchMapping("/{id}") @Transactional public Response update(Authentication a,@PathVariable Long id,@RequestBody Update r){DailyChecklistItem i=owned(a,id);if(r.title()!=null)i.setTitle(r.title().trim());if(r.position()!=null)i.setPosition(r.position());if(r.done()!=null){i.setDone(r.done());i.setDoneAt(r.done()?Instant.now():null);}return response(items.save(i));}
    @DeleteMapping("/{id}") @Transactional public ResponseEntity<Void> delete(Authentication a,@PathVariable Long id){items.delete(owned(a,id));return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/carry-over") @Transactional public Response carry(Authentication a,@PathVariable Long id,@RequestBody Carry r){DailyChecklistItem o=owned(a,id);DailyChecklistItem copy=DailyChecklistItem.builder().user(o.getUser()).date(r.toDate()).title(o.getTitle()).delo(o.getDelo()).position(0).done(false).build();return response(items.save(copy));}
    private User user(Authentication a){return users.findByUsername(a.getName()).orElseThrow();} private DailyChecklistItem owned(Authentication a,Long id){return items.findByIdAndUser(id,user(a)).orElseThrow();} private Response response(DailyChecklistItem i){return new Response(i.getId(),i.getDate(),i.getTitle(),i.getDelo()==null?null:i.getDelo().getId(),i.isDone(),i.getDoneAt(),i.getPosition());}
    public record Request(LocalDate date,@NotBlank String title,Long deloId,Integer position){} public record Update(String title,Integer position,Boolean done){} public record Carry(LocalDate toDate){} public record Response(Long id,LocalDate date,String title,Long deloId,boolean done,Instant doneAt,Integer position){}
}
