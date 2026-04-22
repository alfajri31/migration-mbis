package com.example.migrasi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "m_lob")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lob extends BaseAuditEntity {
    @Id
    private String id;
    private String lobName;
}
