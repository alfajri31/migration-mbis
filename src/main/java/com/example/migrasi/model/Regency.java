package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mst_kota")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Regency {

    @Id
    private Integer id;

    @Column(name = "nama", length = 100)
    private String nama;

    @Column(name = "id_provinsi", length = 100)
    private Integer idProvinsi;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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
