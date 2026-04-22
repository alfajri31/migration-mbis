package com.example.migrasi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "m_pipeline_stage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStage extends BaseAuditEntity {
    @Id
    private UUID id;
    private String code;
    private String stageName;
    private String stageCode;
    private int sortOrderDetail;
    private int sortOrder;
    private String description;
    @Column(name = "stage_type")
    private String typeCode;
    @Column(name = "is_shown")
    private boolean isActive;
}
