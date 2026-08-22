package ru.wolf.api.importxlsx;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "xlsx_import_question")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class XlsxImportQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_run_id") private XlsxImportRun importRun;
    @Column(name = "activity_text", nullable = false, length = 500) private String activityText;
    @Column(name = "sheet_name", nullable = false) private String sheetName;
    @Column(name = "start_at", nullable = false) private LocalDateTime startAt;
    @Column(nullable = false) private boolean resolved;
}
