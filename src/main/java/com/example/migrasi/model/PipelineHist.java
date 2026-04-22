package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_pipeline_hist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineHist extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID pipelineId;
    private UUID userId;
    private String stageCode;
    private String status;
    private UUID createdBy;
    private String toStage;
    private String fromStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipelineId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Pipeline pipeline;

    private UUID toStageDetailId;
    private UUID fromStageDetailId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fromStage",referencedColumnName = "code", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private PipelineStage from;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "toStage",referencedColumnName = "code", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private PipelineStage to;

    @OneToMany(mappedBy = "history", fetch = FetchType.EAGER)
    private List<Activity> histories = new ArrayList<>();

    private boolean isFixedStage;
}
