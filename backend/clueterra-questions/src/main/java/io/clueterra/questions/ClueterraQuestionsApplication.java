package io.clueterra.questions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ClueterraQuestionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClueterraQuestionsApplication.class, args);
    }
}
