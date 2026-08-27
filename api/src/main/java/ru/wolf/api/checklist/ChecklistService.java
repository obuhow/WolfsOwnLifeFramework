package ru.wolf.api.checklist;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.checklist.dto.*;
import ru.wolf.api.checklist.DailyChecklistItem;
import ru.wolf.api.delo.DeloRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service @RequiredArgsConstructor
public class ChecklistService {
    private final DailyChecklistItemRepository items;
    private final UserRepository users;
    private final DeloRepository delos;
    @Transactional(readOnly=true)
    public List<Response> list(String username, LocalDate date){return items.findForDate(user(username),date).stream().map(this::response).toList();}
    @Transactional
    public Response create(String username, Request r){User u=user(username); DailyChecklistItem i=DailyChecklistItem.builder().user(u).date(r.date()).title(r.title().trim()).position(r.position()==null?0:r.position()).done(false).delo(r.deloId()==null?null:delos.findByUserAndId(u,r.deloId()).orElseThrow()).build();return response(items.save(i));}
    @Transactional
    public Response update(String username,Long id,Update r){DailyChecklistItem i=owned(username,id);if(r.title()!=null)i.setTitle(r.title().trim());if(r.position()!=null)i.setPosition(r.position());if(r.done()!=null){i.setDone(r.done());i.setDoneAt(r.done()?Instant.now():null);}return response(items.save(i));}
    @Transactional
    public void delete(String username,Long id){items.delete(owned(username,id));}
    @Transactional
    public Response carry(String username,Long id,Carry r){DailyChecklistItem o=owned(username,id);DailyChecklistItem copy=DailyChecklistItem.builder().user(o.getUser()).date(r.toDate()).title(o.getTitle()).delo(o.getDelo()).position(0).done(false).build();return response(items.save(copy));}
    private User user(String username){return users.findByUsername(username).orElseThrow();}
    private DailyChecklistItem owned(String username,Long id){return items.findByIdAndUser(id,user(username)).orElseThrow();}
    private Response response(DailyChecklistItem i){return new Response(i.getId(),i.getDate(),i.getTitle(),i.getDelo()==null?null:i.getDelo().getId(),i.isDone(),i.getDoneAt(),i.getPosition());}
}
