package ru.wolf.api.importxlsx;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface XlsxImportQuestionRepository extends JpaRepository<XlsxImportQuestion, Long> {
    List<XlsxImportQuestion> findByImportRunIdAndResolvedFalseOrderByStartAtAsc(Long importRunId);
}
