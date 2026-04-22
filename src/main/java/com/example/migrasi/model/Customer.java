package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String picName;
    @Column(name = "branch_code")
    private String branchId;
    private String npwp;
    private String address;
    private String fotoFile;
    @Column(name = "province_id")
    private Integer idProvinsi;
    @Column(name = "kota_id")
    private Integer idKota;
    @Column(name = "kecamatan_id")
    private Integer idKecamatan;
    @Column(name = "kelurahan_id")
    private Long idKelurahan;
    private String customerType;
    private String entityType;
    private String companyName;
    @Column(name = "npwp_normalized")
    private String npwpNormalized;
    @Column(name = "phone")
    private String picPhone;
    private String phoneNormalized;
    private String email;
    private String kota;
    private String kecamatan;
    private String kelurahan;
    private String postalCode;
    private UUID createdBy;
    private String customerStatus;
    private String bumnCategory;
    private String cif;
    private String nip;
    private boolean isFixedAssignment;
    private Integer cityId;
    private Integer totalEmployeeNumber;
    private String skBumn;
    private Integer totalWonCount;
    private Integer totalLostCount;
    private BigDecimal totalPremiLifetime;
    private UUID assignedRmId;
    private Date assignmentDate;
    private String assignmentNotes;
    @Column(name = "is_branch_hq")
    private boolean branchHq;
    private String customerNik;
    private String normalizedCompanyName;
    private boolean isMigrated;
    private String customerNib;
    private UUID pipelineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipelineId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Pipeline pipeline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Province province;
    private String picPosition;
}
