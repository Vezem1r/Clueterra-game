package io.clueterra.api.config;

import org.apache.catalina.valves.JsonErrorReportValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatErrorHandlingConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatJsonErrorReportCustomizer() {
        return factory -> {
            JsonErrorReportValve valve = new JsonErrorReportValve();
            valve.setShowReport(false);
            valve.setShowServerInfo(false);
            factory.addEngineValves(valve);
        };
    }
}
