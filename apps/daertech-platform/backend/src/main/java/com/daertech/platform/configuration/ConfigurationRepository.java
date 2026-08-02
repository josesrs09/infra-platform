package com.daertech.platform.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationRepository extends JpaRepository<ConfigurationItem, UUID> {
    List<ConfigurationItem> findAllByEnvironmentOrderByCategoryAscKeyAsc(String environment);
    Optional<ConfigurationItem> findByEnvironmentAndKey(String environment, String key);
}
