package com.example.migrasi.model;

import com.example.migrasi.model.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

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
}
