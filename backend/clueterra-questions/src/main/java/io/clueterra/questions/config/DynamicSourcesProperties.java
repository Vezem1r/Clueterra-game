package io.clueterra.questions.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DynamicSourcesProperties {
    private CronScheduleProperties globalBrands = new CronScheduleProperties("0 0 5 1 1 *");
}
