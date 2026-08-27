package ru.wolf.api.gantt.dto;
import java.math.BigDecimal;
import java.util.List;
import ru.wolf.api.project.Project;
public record ProjectRow(Long id,Long parentId,Long lifeAreaId,String lifeAreaName,String title,String startDate,String endDate,BigDecimal totalPlanHours,Project.PlanDistribution planDistribution,int depth,List<CellHours> cells) {}
