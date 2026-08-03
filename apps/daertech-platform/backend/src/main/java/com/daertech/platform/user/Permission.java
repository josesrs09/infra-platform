package com.daertech.platform.user;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="permissions", schema="platform")
public class Permission {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=120) private String code;
    @Column(nullable=false, length=160) private String name;
    @Column(nullable=false, length=80) private String module;
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getModule(){return module;}
}
