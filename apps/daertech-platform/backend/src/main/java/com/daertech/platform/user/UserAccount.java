package com.daertech.platform.user;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "platform")
public class UserAccount {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=80) private String username;
    @Column(nullable=false, unique=true, length=180) private String email;
    @Column(name="full_name", nullable=false, length=180) private String fullName;
    @Column(name="password_hash", nullable=false) private String passwordHash;
    @Column(nullable=false) private boolean enabled = true;
    @Column(nullable=false) private boolean locked = false;
    @Column(name="last_login_at") private OffsetDateTime lastLoginAt;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name="user_roles", schema="platform", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id"))
    private Set<Role> roles = new HashSet<>();

    @PrePersist void prePersist(){ if(id==null) id=UUID.randomUUID(); var now=OffsetDateTime.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void preUpdate(){ updatedAt=OffsetDateTime.now(); }
    public UUID getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public boolean isLocked(){return locked;} public void setLocked(boolean v){locked=v;} public OffsetDateTime getLastLoginAt(){return lastLoginAt;} public void setLastLoginAt(OffsetDateTime v){lastLoginAt=v;}
    public Set<Role> getRoles(){return roles;}
}
