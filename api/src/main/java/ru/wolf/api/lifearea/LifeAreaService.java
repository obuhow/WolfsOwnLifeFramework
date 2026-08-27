package ru.wolf.api.lifearea;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.lifearea.dto.*;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LifeAreaService {
    private final LifeAreaRepository lifeAreaRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LifeAreaResponse> list(String username) { return lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(currentUser(username)).stream().map(LifeAreaResponse::from).toList(); }
    @Transactional
    public LifeAreaResponse create(String username, CreateLifeAreaRequest request) {
        User user = currentUser(username);
        if (lifeAreaRepository.existsByUserAndName(user, request.name())) throw new IllegalArgumentException("Область жизни с таким именем уже существует");
        LifeArea area = LifeArea.builder().user(user).name(request.name()).color(request.color()).sortOrder(lifeAreaRepository.findMaxSortOrderByUser(user) + 1).build();
        return LifeAreaResponse.from(lifeAreaRepository.save(area));
    }
    @Transactional
    public LifeAreaResponse update(String username, Long id, UpdateLifeAreaRequest request) {
        User user = currentUser(username); LifeArea area = find(user, id);
        if (!area.getName().equals(request.name()) && lifeAreaRepository.existsByUserAndName(user, request.name())) throw new IllegalArgumentException("Область жизни с таким именем уже существует");
        area.setName(request.name()); area.setColor(request.color()); return LifeAreaResponse.from(lifeAreaRepository.save(area));
    }
    @Transactional public void delete(String username, Long id) { lifeAreaRepository.delete(find(currentUser(username), id)); }
    @Transactional
    public LifeAreaResponse move(String username, Long id, MoveLifeAreaRequest request) {
        User user = currentUser(username); LifeArea area = find(user, id); List<LifeArea> all = lifeAreaRepository.findByUserOrderBySortOrderAscNameAsc(user);
        int old = -1; for (int i=0;i<all.size();i++) if (all.get(i).getId().equals(area.getId())) { old=i; break; }
        if (old < 0) throw new IllegalArgumentException("Область жизни не найдена");
        int target = Math.max(0, Math.min(request.newIndex(), all.size()-1)); if (old == target) return LifeAreaResponse.from(area);
        LifeArea moved=all.remove(old); all.add(target,moved); for(int i=0;i<all.size();i++) all.get(i).setSortOrder(i); lifeAreaRepository.saveAll(all); return LifeAreaResponse.from(moved);
    }
    private User currentUser(String name) { return userRepository.findByUsername(name).orElseThrow(() -> new IllegalStateException("User not found")); }
    private LifeArea find(User user, Long id) { return lifeAreaRepository.findByUserAndId(user,id).orElseThrow(() -> new IllegalArgumentException("Область жизни не найдена")); }
}
