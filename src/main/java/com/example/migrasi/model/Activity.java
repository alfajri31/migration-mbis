package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_activity")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID pipelineId;

    private UUID tenderId;

    private UUID tenderStageId;

    private Boolean isTenderActivity;

    private String activityType;

    @Column(name = "activity_title")
    private String activityTitle;

    @Column(name = "activity_description")
    private String actionDescription;

    private OffsetDateTime scheduledDate;

    private OffsetDateTime actualStartTime;

    private OffsetDateTime actualEndTime;

    private Integer durationMinutes;

    private BigDecimal gpsLatitude;

    private BigDecimal gpsLongitude;

    private BigDecimal gpsAccuracyMeters;

    private OffsetDateTime gpsCapturedAt;

    private String locationAddress;

    private String locationType;

    private String locationNotes;

    private Boolean isGpsVerified;

    private Integer gpsDistanceFromCustomerMeters;

    private String photoUrl;

    private String photoFilename;

    private Long photoFilesizeBytes;

    private OffsetDateTime photoCapturedAt;

    private BigDecimal photoLatitude;

    private BigDecimal photoLongitude;

    @Column(name = "primary_rm_id")
    private UUID rmId;

    private String activityStatus;

    private String outcomeStatus;

    private String outcomeSummary;

    private String outcomeDetails;

    private String customerInterestLevel;

    private String customerFeedback;

    private Boolean triggeredStageChange;

    private String stageBefore;

    private String stageAfter;

    private String stageDetailBeforeId;
    private String stageDetailAfterId;
    private String stageDetailAfterCode;
    private String stageDetailBeforeCode;

    private Boolean nextActionRequired;

    private String nextActionDescription;

    private OffsetDateTime nextActionDueDate;

    private UUID nextActionAssignedTo;

    private Boolean hasAttachments;

    private Integer attachmentsCount;

    private String appVersion;

    @Column(name = "is_offline_created")
    private boolean isRequired;

    private OffsetDateTime syncedAt;

    private UUID createdBy;

    private UUID pipelineHistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipelineHistId", insertable = false, updatable = false)
    private PipelineHist history;

    /* JSONB fields */
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String customerParticipants;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String internalParticipants;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String objectionsRaised;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String deviceInfo;

    @OneToMany(mappedBy = "activity", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ActivityAttachment> attachments = new ArrayList<>();

}
