package io.clueterra.questions.config;

import io.clueterra.questions.clients.common.dto.SourceProperties;
import io.clueterra.questions.entity.enums.DataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "data")
public class SourcesProperties {

    private Map<String, SourceProperties> sources = new HashMap<>();

    public SourceProperties getSource(DataSource source) {
        SourceProperties props = sources.get(source.getConfigKey());
        if (props == null) {
            throw new IllegalStateException("No configuration found for data source: " + source);
        }
        return props;
    }
}
