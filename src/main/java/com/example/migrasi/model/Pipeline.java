package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_pipeline")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pipeline extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private UUID customerId;
    private String pipelineNumber;
    private String businessSource;
    private String businessSourceName;
    private String customerCompanyName;
    private String customerPostalCode;
    private String customerKota;
    private String customerKecamatan;
    private String customerKelurahan;
    private String entityType;
    private String customerLegalType;
    private String customerCategory;
    private String customerPicName;
    private String customerPicPosition;
    private String customerPhone;
    private String customerEmail;
    private String customerNpwp;
    private String customerNib;
    private String customerAddress;
    private Integer customerProvinceId;
    private Integer customerTotalEmployeeNumber;
    private String customerBusinessSector;
    private String currency;
    private String currencyCode;
    private String premiumCalculationFormula;
    private Integer customerCityId;
    private Integer customerDistrictId;
    private Long customerSubDistrictId;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalPremi;
    @Column(precision = 19, scale = 6)
    private BigDecimal premiumRate;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalRate;
    @Column(precision = 19, scale = 2)
    private BigDecimal discountPercentage;
    @Column(precision = 19, scale = 2)
    private BigDecimal finalPremi;
    @Column(precision = 19, scale = 2)
    private BigDecimal tsi;

    private UUID productId;
    private String lobCode;
    private String cobCode;
    private String pipelineType;
    @Column(name = "current_stage_code")
    private String currentStageCode;
    private UUID currentStageDetailId;
    private UUID createdBy;
    private String currentStageDetailCode;
    private String currentStageDetailName;
    private UUID nextStageDetailId;
    private String nextStageDetailCode;
    private String nextStageDetailName;
    private String branchId;
    private boolean hasTenderProcess;
    @Column(name = "is_customer_complete")
    private boolean customerComplete;
    private OffsetDateTime estimatedClosingDate;
    @Column(name = "is_branch_hq")
    private boolean branchHq;
    private String pipelineStatus;
    private String customerNik;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Product product;

    @OneToMany(mappedBy = "pipeline", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<PipelineHist> histories = new ArrayList<>();

    private String normalizedCompanyName;

    @OneToMany(mappedBy = "pipeline", fetch = FetchType.LAZY)
    private List<Customer> customers = new ArrayList<>();

    private String role;
    private UUID parentParticipantId;
    private String customerProvinsi;
    private String wonPolicyNumber;
    private String notes;
    private String lostNotes;
    private OffsetDateTime wonAt;
    private OffsetDateTime lostAt;
}
