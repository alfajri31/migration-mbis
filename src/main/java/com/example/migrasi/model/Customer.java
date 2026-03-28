package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.sql.Date;
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
    private String npwpNormalized;
    @Column(name = "phone")
    private String picPhone;
    private String phoneNormalized;
    private String email;
    private String kota;
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
    private String normalizedCompanyName;
    private boolean isMigrated;
}
