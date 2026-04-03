package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "m_users")
@Setter
@Getter
public class User extends BaseAuditEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String nip;

    @Column(name = "email")
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "client_type")
    private String clientType;

    @Column(name = "last_login")
    private OffsetDateTime lastLogin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String lastLoginResponse;

    @Column(name = "is_branch_hq")
    private boolean branchHq;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<UserMapping> userMappings = new ArrayList<>();

    private String divisionCode;

    @Transient
    private String branchCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "divisionCode", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Division division;
}
