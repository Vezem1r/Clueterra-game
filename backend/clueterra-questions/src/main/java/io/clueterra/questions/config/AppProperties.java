package io.clueterra.questions.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "clueterra")
public class AppProperties {

    private HintsProperties hints = new HintsProperties();
    private ChallengeProperties challenge = new ChallengeProperties();
    private SyncProperties sync = new SyncProperties();
    private DynamicSourcesProperties dynamic = new DynamicSourcesProperties();
    private HealthProperties health = new HealthProperties();
}
