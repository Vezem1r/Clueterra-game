package io.clueterra.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import okhttp3.OkHttpClient;

@Configuration(proxyBeanMethods = false)
public class BudgetHttpClientConfig {

    @Bean
    public OkHttpClient externalSourceHttpClient(ExternalHttpProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout())
                .callTimeout(properties.callTimeout())
                .addInterceptor(
                        new RetryingGetInterceptor(
                                properties.maxAttempts(), properties.retryBaseDelay(), properties.retryMaxDelay()))
                .retryOnConnectionFailure(false)
                .build();
    }
}
