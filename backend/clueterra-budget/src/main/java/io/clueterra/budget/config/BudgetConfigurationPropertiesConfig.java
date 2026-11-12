package io.clueterra.budget.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.clueterra.budget.storage.BudgetStorageProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  BudgetEnrichmentProperties.class,
  BudgetGenerationProperties.class,
  BudgetInternalProperties.class,
  BudgetStorageProperties.class,
  BudgetSyncProperties.class,
  ExchangeRateProperties.class,
  ExternalHttpProperties.class,
  WikidataProperties.class,
  WikimediaProperties.class
})
public class BudgetConfigurationPropertiesConfig {}
