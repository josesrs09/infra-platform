package com.daertech.platform.configuration;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="configuration_items", schema="platform", uniqueConstraints=@UniqueConstraint(columnNames={"environment","config_key"}))
public class ConfigurationItem {
    @Id private UUID id;
    @Column(nullable=false,length=80) private String category;
    @Column(name="config_key",nullable=false,length=180) private String key;
    @Column(name="config_value",columnDefinition="text") private String value;
    @Column(nullable=false) private boolean secret;
    @Column(nullable=false,length=40) private String environment;
    @Column(name="value_type",nullable=false,length=30) private String valueType="STRING";
    @Column(length=500) private String description;
    @Column(name="validation_rule",length=500) private String validationRule;
    @Column(nullable=false) private boolean active=true;
    @Column(nullable=false) private long version=1;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @PrePersist void prePersist(){if(id==null) id=UUID.randomUUID(); if(updatedAt==null) updatedAt=OffsetDateTime.now();}
    @PreUpdate void preUpdate(){updatedAt=OffsetDateTime.now();}
    public UUID getId(){return id;} public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getKey(){return key;} public void setKey(String v){key=v;} public String getValue(){return value;} public void setValue(String v){value=v;}
    public boolean isSecret(){return secret;} public void setSecret(boolean v){secret=v;} public String getEnvironment(){return environment;} public void setEnvironment(String v){environment=v;}
    public String getValueType(){return valueType;} public void setValueType(String v){valueType=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getValidationRule(){return validationRule;} public void setValidationRule(String v){validationRule=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;} public OffsetDateTime getUpdatedAt(){return updatedAt;}
}
