package ru.wolf.api.lifesphere;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wolf.api.idea.IdeaRepository;
import ru.wolf.api.lifesphere.dto.*;
import ru.wolf.api.project.Project;
import ru.wolf.api.project.ProjectRepository;
import ru.wolf.api.routine.Routine;
import ru.wolf.api.routine.RoutineRepository;
import ru.wolf.api.user.User;
import ru.wolf.api.user.UserRepository;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SynergyService {
 private final SynergyRepository synergyRepository; private final LifeSphereRepository lifeSphereRepository; private final ProjectRepository projectRepository; private final UserRepository userRepository; private final IdeaRepository ideaRepository; private final RoutineRepository routineRepository;
 @Transactional public SynergyResponse create(String name,CreateSynergyRequest r){User u=user(name);LifeSphere s=lifeSphere(u,r.sphereId());Project p=r.projectId()==null?null:projectRepository.findByUserAndId(u,r.projectId()).orElseThrow(()->new IllegalArgumentException("Проект не найден"));Routine rt=r.routineId()==null?null:routineRepository.findByUserAndId(u,r.routineId()).orElseThrow(()->new IllegalArgumentException("Рутина не найдена"));if(r.ideaId()!=null)ideaRepository.findByUserAndId(u,r.ideaId()).orElseThrow(()->new IllegalArgumentException("Идея не найдена"));if((p==null?0:1)+(r.ideaId()==null?0:1)+(rt==null?0:1)!=1)throw new IllegalArgumentException("Должен быть указан ровно один из: projectId, ideaId или routineId");if(p!=null&&synergyRepository.existsByUserAndProjectAndSphere(u,p,s))throw new IllegalArgumentException("Связь между этим проектом и сферой уже существует");if(r.ideaId()!=null&&synergyRepository.existsByUserAndIdeaIdAndSphere(u,r.ideaId(),s))throw new IllegalArgumentException("Связь между этой идеей и сферой уже существует");if(rt!=null&&synergyRepository.existsByUserAndRoutineAndSphere(u,rt,s))throw new IllegalArgumentException("Связь между этой рутиной и сферой уже существует");return SynergyResponse.from(synergyRepository.save(Synergy.builder().user(u).project(p).ideaId(r.ideaId()).routine(rt).sphere(s).impact(r.impact()).build()));}
 @Transactional(readOnly=true) public List<SynergyResponse> list(String name,Long projectId,Long ideaId,Long routineId){User u=user(name);if((projectId!=null?1:0)+(ideaId!=null?1:0)+(routineId!=null?1:0)>1)throw new IllegalArgumentException("Нельзя одновременно указать несколько владельцев синергии");List<Synergy> ss;if(projectId!=null){Project p=projectRepository.findByUserAndId(u,projectId).orElseThrow(()->new IllegalArgumentException("Проект не найден"));ss=synergyRepository.findByUserAndProjectWithSphere(u,p);}else if(ideaId!=null){ideaRepository.findByUserAndId(u,ideaId).orElseThrow(()->new IllegalArgumentException("Идея не найдена"));ss=synergyRepository.findByUserAndIdeaIdWithSphere(u,ideaId);}else if(routineId!=null){Routine r=routineRepository.findByUserAndId(u,routineId).orElseThrow(()->new IllegalArgumentException("Рутина не найдена"));ss=synergyRepository.findByUserWithSphere(u).stream().filter(x->x.getRoutine()!=null&&x.getRoutine().getId().equals(r.getId())).toList();}else ss=synergyRepository.findByUserWithSphere(u);return ss.stream().map(SynergyResponse::from).toList();}
 @Transactional public void delete(String name,Long id){synergyRepository.delete(synergyRepository.findByUserAndId(user(name),id).orElseThrow(()->new IllegalArgumentException("Синергия не найдена")));}
 @Transactional public SynergyResponse update(String name,Long id,UpdateSynergyRequest r){User u=user(name);Synergy s=synergyRepository.findByUserAndId(u,id).orElseThrow(()->new IllegalArgumentException("Синергия не найдена"));s.setImpact(r.impact());Synergy saved=synergyRepository.save(s);return SynergyResponse.from(synergyRepository.findByUserAndId(u,saved.getId()).orElseThrow());}
 private User user(String n){return userRepository.findByUsername(n).orElseThrow(()->new IllegalStateException("User not found"));} private LifeSphere lifeSphere(User u,Long id){return lifeSphereRepository.findByUserAndId(u,id).orElseThrow(()->new IllegalArgumentException("Сфера жизни не найдена"));}
}
