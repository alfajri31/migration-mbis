package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "r_user_application")
@Setter
@Getter
public class RCabangDelegation extends BaseAuditEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false)
    private Long id;

}
