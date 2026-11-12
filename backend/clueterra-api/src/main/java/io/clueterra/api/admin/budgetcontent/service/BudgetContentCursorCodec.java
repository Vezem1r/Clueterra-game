package io.clueterra.api.admin.budgetcontent.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import io.clueterra.api.admin.budgetcontent.repository.BudgetContentCursor;

@Component
public class BudgetContentCursorCodec {

    public String encode(BudgetContentCursor cursor) {
        String value =
                cursor.qualityScore()
                        + ":"
                        + cursor.monthlyPageviews()
                        + ":"
                        + cursor.wikidataId()
                        + ":"
                        + cursor.category();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public BudgetContentCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", 4);
            if (parts.length != 4
                    || !parts[2].matches("^Q[1-9][0-9]*$")
                    || !parts[3].matches("^[A-Z_]+$")) {
                throw new IllegalArgumentException("Invalid cursor");
            }
            return new BudgetContentCursor(
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2], parts[3]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cursor", exception);
        }
    }
}
