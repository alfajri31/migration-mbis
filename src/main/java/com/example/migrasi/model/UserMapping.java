package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.util.UUID;

@Entity
@Table(name = "m_mapping_users")
@Setter @Getter
public class UserMapping extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private User user;

    private String divisionId;
    private String branchId;
    private UUID assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branchId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Branch branch;

}