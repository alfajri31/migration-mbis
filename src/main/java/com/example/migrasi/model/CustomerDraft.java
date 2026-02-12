package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mst_customer_draft")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_user", length = 100)
    private String idUser;

    @Column(length = 100)
    private String cif;

    @Column(name = "id_company_profile", length = 200)
    private String idCompanyProfile;

    @Column(name = "bentuk_customer", length = 100)
    private String bentukCustomer;

    @Column(name = "nama_customer", length = 200)
    private String namaCustomer;

    @Column(name = "nama_rm", length = 200)
    private String namaRm;

    @Column(length = 200)
    private String cabang;

    @Column(name = "no_tlp_perusahaan", length = 50)
    private String noTlpPerusahaan;

    @Column(length = 50)
    private String npwp;

    @Column(length = 500)
    private String alamat;

    @Column(length = 200)
    private String foto;

    @Column(name = "id_provinsi")
    private Integer idProvinsi;

    @Column(name = "id_kota")
    private Integer idKota;

    @Column(name = "id_kecamatan")
    private Integer idKecamatan;

    @Column(name = "id_kelurahan")
    private Integer idKelurahan;

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
