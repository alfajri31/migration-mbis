package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mst_customers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", length = 100)
    private UUID idUser;

    @Column(length = 100)
    private String cif;

    @Column(name = "bentuk_customer", length = 100)
    private String bentukCustomer;

    @Column(name = "name_customer", length = 200)
    private String namaCustomer;

    @Column(name = "name_rm", length = 200)
    private String namaRm;

    @Column(name = "branch_id", length = 200)
    private UUID branchId;

    @Column(name = "no_tlp_perusahaan", length = 50)
    private String noTlpPerusahaan;

    @Column(length = 50)
    private String npwp;

    @Column(length = 500)
    private String address;

    @Column(length = 200)
    private String foto;

    @Column(name = "province_id")
    private Integer idProvinsi;

    @Column(name = "kota_id")
    private Integer idKota;

    @Column(name = "kecamatan_id")
    private Integer idKecamatan;

    @Column(name = "kelurahan_id")
    private Long idKelurahan;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // =========================
    // Auto Timestamp
    // =========================
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
