package com.daertech.platform.user;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name="roles", schema="platform")
public class Role {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=80) private String code;
    @Column(nullable=false, length=120) private String name;
    @Column(length=255) private String description;
    @Column(nullable=false) private boolean active = true;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="role_permissions", schema="platform", joinColumns=@JoinColumn(name="role_id"), inverseJoinColumns=@JoinColumn(name="permission_id"))
    private Set<Permission> permissions = new HashSet<>();
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public boolean isActive(){return active;} public Set<Permission> getPermissions(){return permissions;}
}
