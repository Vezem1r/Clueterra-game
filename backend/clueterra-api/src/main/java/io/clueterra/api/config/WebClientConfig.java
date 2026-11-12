package io.clueterra.api.config;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final QuestionsServiceProperties questionsServiceProperties;
    private final BudgetServiceProperties budgetServiceProperties;

    @Bean
    public WebClient questionsServiceClient() {
        return internalServiceClient(
                questionsServiceProperties.getBaseUrl(),
                questionsServiceProperties.getSecret());
    }

    @Bean
    public WebClient budgetServiceClient() {
        return internalServiceClient(
                budgetServiceProperties.getBaseUrl(),
                budgetServiceProperties.getSecret());
    }

    private WebClient internalServiceClient(String baseUrl, String secret) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (StringUtils.isNotBlank(secret)) {
            builder.defaultHeader("X-Internal-Token", secret);
        }

        return builder.build();
    }
}
