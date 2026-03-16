package com.example.migrasi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "m_branches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Branch extends BaseAuditEntity {
    @Id
    private String id;
    @Column(name = "code", length = 200)
    private String code;
    @Column(name = "name", length = 200)
    private String name;
    private String region;
}
