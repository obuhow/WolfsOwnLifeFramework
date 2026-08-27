package ru.wolf.api.lifesphere;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.lifesphere.dto.*;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LifeSphereService {
    private final LifeSphereRepository lifeSphereRepository;
    private final UserRepository userRepository;
    @Transactional(readOnly=true) public List<LifeSphereResponse> list(String name){return lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(user(name)).stream().map(LifeSphereResponse::from).toList();}
    @Transactional public LifeSphereResponse create(String name, CreateLifeSphereRequest r){User u=user(name);if(lifeSphereRepository.existsByUserAndName(u,r.name()))throw new IllegalArgumentException("Сфера жизни с таким именем уже существует");return LifeSphereResponse.from(lifeSphereRepository.save(LifeSphere.builder().user(u).name(r.name()).color(r.color()).sortOrder(lifeSphereRepository.findMaxSortOrderByUser(u)+1).build()));}
    @Transactional public LifeSphereResponse update(String name,Long id,UpdateLifeSphereRequest r){User u=user(name);LifeSphere s=find(u,id);if(!s.getName().equals(r.name())&&lifeSphereRepository.existsByUserAndName(u,r.name()))throw new IllegalArgumentException("Сфера жизни с таким именем уже существует");s.setName(r.name());s.setColor(r.color());return LifeSphereResponse.from(lifeSphereRepository.save(s));}
    @Transactional public void delete(String name,Long id){lifeSphereRepository.delete(find(user(name),id));}
    @Transactional public LifeSphereResponse archive(String name,Long id){User u=user(name);LifeSphere s=find(u,id);s.setArchived(!s.isArchived());return LifeSphereResponse.from(lifeSphereRepository.save(s));}
    @Transactional public LifeSphereResponse move(String name,Long id,MoveLifeSphereRequest r){User u=user(name);LifeSphere s=find(u,id);List<LifeSphere>a=lifeSphereRepository.findByUserOrderBySortOrderAscNameAsc(u);int old=-1;for(int i=0;i<a.size();i++)if(a.get(i).getId().equals(s.getId())){old=i;break;}if(old<0)throw new IllegalArgumentException("Сфера жизни не найдена");int n=Math.max(0,Math.min(r.newIndex(),a.size()-1));if(old==n)return LifeSphereResponse.from(s);LifeSphere m=a.remove(old);a.add(n,m);for(int i=0;i<a.size();i++)a.get(i).setSortOrder(i);lifeSphereRepository.saveAll(a);return LifeSphereResponse.from(m);}
    private User user(String n){return userRepository.findByUsername(n).orElseThrow(()->new IllegalStateException("User not found"));}
    private LifeSphere find(User u,Long id){return lifeSphereRepository.findByUserAndId(u,id).orElseThrow(()->new IllegalArgumentException("Сфера жизни не найдена"));}
}
