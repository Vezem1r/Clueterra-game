package io.clueterra.questions.clients.openstreetmap.dto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record CountryPbfFile(Path path, String countryCode, String countryName) {

    public long fileSizeBytes() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    public String fileSizeFormatted() {
        long bytes = fileSizeBytes();
        if (bytes < 0) {
            return "unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
