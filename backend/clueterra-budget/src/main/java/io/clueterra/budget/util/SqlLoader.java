package io.clueterra.budget.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

public final class SqlLoader {

    private SqlLoader() {
    }

    public static String load(Class<?> repositoryClass, String queryName) {
        String path = "sql/" + repositoryClass.getSimpleName() + "/" + queryName + ".sql";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL resource not found: " + path);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load SQL resource: " + path, exception);
        }
    }
}
