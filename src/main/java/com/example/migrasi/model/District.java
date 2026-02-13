package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mst_kecamatan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class District {

    @Id
    private Integer id;

    @Column(name = "name", length = 100)
    private String nama;

    @Column(name = "kota_id", length = 100)
    private Integer idKota;

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
