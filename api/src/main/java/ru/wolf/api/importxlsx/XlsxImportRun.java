package ru.wolf.api.importxlsx;

import jakarta.persistence.*;
import lombok.*;
import ru.wolf.api.user.User;

import java.time.Instant;

@Entity
@Table(name = "xlsx_import_run", uniqueConstraints = @UniqueConstraint(name = "uk_xlsx_import_user_hash", columnNames = {"user_id", "file_hash"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class XlsxImportRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false) private String filename;
    @Column(name = "file_hash", nullable = false, length = 128) private String fileHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int totalCells;
    @Column(nullable = false) private int mapped;
    @Column(nullable = false) private int unknown;
    @Column(nullable = false) private int pendingQuestions;
    @Column(nullable = false) private Instant createdAt;

    public enum Status { PAUSED, DONE }
    @PrePersist void created() { createdAt = Instant.now(); }
}

