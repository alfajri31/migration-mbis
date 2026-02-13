package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mst_kelurahan")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Village {

    @Id
    private Long id;

    @Column(name = "name", length = 100)
    private String nama;

    @Column(name = "kecamatan_id", length = 100)
    private Integer idKecamatan;

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
