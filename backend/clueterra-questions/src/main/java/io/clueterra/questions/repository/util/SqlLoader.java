package io.clueterra.questions.repository.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SqlLoader {

    private SqlLoader() {
    }

    public static String load(Class<?> repositoryClass, String queryName) {
        String path = "sql/" + repositoryClass.getSimpleName() + "/" + queryName + ".sql";

        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new SqlResourceLoadingException("Sql file not found: " + path);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new SqlResourceLoadingException("Error while loading Sql file: " + path, e);
        }
    }
}
