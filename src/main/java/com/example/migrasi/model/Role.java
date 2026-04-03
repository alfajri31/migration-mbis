package com.example.migrasi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "m_role")
@Setter
@Getter
public class Role extends BaseAuditEntity {

    @Id
    private String id;
    private String name;
}
