package io.clueterra.budget.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BudgetClockConfig {

    @Bean
    public Clock budgetClock() {
        return Clock.systemUTC();
    }
}
