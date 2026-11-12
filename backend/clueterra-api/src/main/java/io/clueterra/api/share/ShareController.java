package io.clueterra.api.share;

import static io.clueterra.api.share.dto.ShareCardConstants.formatDifficulty;

import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.clueterra.api.common.utils.Base62;
import io.clueterra.api.share.dto.BudgetShareData;
import io.clueterra.api.share.dto.ShareData;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import io.clueterra.api.config.OAuthProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/s")
@RequiredArgsConstructor
@Tag(name = "Share", description = "Public share endpoints for game result cards")
public class ShareController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final CacheControl CACHE_1D = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();
    private static final CacheControl CACHE_1D_IMMUTABLE =
            CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic().immutable();

    private static final String OG_HTML =
            """
                    <!DOCTYPE html><html><head>
                    <meta charset="UTF-8">
                    <meta property="og:title" content="%s">
                    <meta property="og:description" content="%s">
                    <meta property="og:image" content="%s">
                    <meta property="og:image:secure_url" content="%s">
                    <meta property="og:image:type" content="image/png">
                    <meta property="og:image:width" content="1200">
                    <meta property="og:image:height" content="630">
                    <meta property="og:url" content="%s">
                    <meta name="twitter:card" content="summary_large_image">
                    <meta name="twitter:image" content="%s">
                    </head><body>
                    <script>window.location.replace("%s")</script>
                    </body></html>""";

    private final ShareSessionLoader sessionLoader;
    private final BudgetShareSessionLoader budgetSessionLoader;
    private final ShareImageRenderer imageRenderer;
    private final OAuthProperties oauthProperties;

    @GetMapping("/{code}")
    @Operation(summary = "OG meta HTML page for share link preview")
    public ResponseEntity<String> sharePage(@PathVariable String code) {
        return decodeSessionId(code)
                .flatMap(sessionLoader::load)
                .map(data -> buildHtmlResponse(code, data))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/og.png")
    @Operation(summary = "Generated OG image for share card")
    public ResponseEntity<byte[]> shareImage(@PathVariable String code) {
        return decodeSessionId(code)
                .flatMap(sessionLoader::load)
                .flatMap(imageRenderer::render)
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CACHE_1D_IMMUTABLE)
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/b/{code}")
    @Operation(summary = "OG meta HTML page for a shared BudgetGuessr result")
    public ResponseEntity<String> budgetSharePage(@PathVariable String code) {
        return decodeSessionId(code)
                .flatMap(budgetSessionLoader::load)
                .map(data -> buildBudgetHtmlResponse(code, data))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/b/{code}/og.png")
    @Operation(summary = "Generated OG image for a BudgetGuessr result")
    public ResponseEntity<byte[]> budgetShareImage(@PathVariable String code) {
        return decodeSessionId(code)
                .flatMap(budgetSessionLoader::load)
                .flatMap(imageRenderer::render)
                .map(png -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CACHE_1D_IMMUTABLE)
                        .body(png))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Optional<Long> decodeSessionId(String code) {
        try {
            return Optional.of(Base62.decode(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ResponseEntity<String> buildHtmlResponse(String code, ShareData data) {
        String publicBaseUrl = trimTrailingSlash(oauthProperties.getFrontendRedirectUrl());
        String shareUrl = publicBaseUrl + "/s/" + code;
        String imageUrl = shareUrl + "/og.png";

        String title = "Clueterra · %s — %s"
                .formatted(data.date().format(DATE_FMT), formatDifficulty(data.level()));

        long totalSec = data.activeTimeMs() / 1000;
        String description = "%d/%d countries · %d:%02d · XP: %,d"
                .formatted(
                        data.guessedCountries(), data.totalCountries(),
                        totalSec / 60, totalSec % 60,
                        data.totalXpEarned());

        String html = OG_HTML.formatted(
                HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(description),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(shareUrl),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(publicBaseUrl));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CACHE_1D)
                .body(html);
    }

    private ResponseEntity<String> buildBudgetHtmlResponse(String code, BudgetShareData data) {
        String publicBaseUrl = trimTrailingSlash(oauthProperties.getFrontendRedirectUrl());
        String shareUrl = publicBaseUrl + "/s/b/" + code;
        String imageUrl = shareUrl + "/og.png";
        String title = "BudgetGuessr · %s — %,d XP"
                .formatted(data.date().format(DATE_FMT), data.totalXpEarned());
        String description = "%.1f%% average accuracy · %d/%d XP · Can you build a better skyline?"
                .formatted(data.averageAccuracy(), data.totalXpEarned(), data.maximumXp());

        String html = OG_HTML.formatted(
                HtmlUtils.htmlEscape(title),
                HtmlUtils.htmlEscape(description),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(shareUrl),
                HtmlUtils.htmlEscape(imageUrl),
                HtmlUtils.htmlEscape(publicBaseUrl + "/budget"));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CACHE_1D)
                .body(html);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
