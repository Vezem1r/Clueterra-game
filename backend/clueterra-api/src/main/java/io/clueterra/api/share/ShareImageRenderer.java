package io.clueterra.api.share;

import static io.clueterra.api.share.dto.ShareCardConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.clueterra.api.share.dto.CountrySquare;
import io.clueterra.api.share.dto.BudgetShareBuilding;
import io.clueterra.api.share.dto.BudgetShareData;
import io.clueterra.api.share.dto.ShareData;
import lombok.Getter;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ShareImageRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String htmlTemplate;
    private String budgetHtmlTemplate;
    private Playwright playwright;
    private Browser browser;
    @Getter
    private boolean available;

    @PostConstruct
    void init() {
        String rawTemplate = loadResource();
        String rawBudgetTemplate = loadResource(BUDGET_TEMPLATE_PATH);
        String boldFontB64 = encodeFont(BOLD_FONT_PATH);
        String mediumFontB64 = encodeFont(MEDIUM_FONT_PATH);

        String fontFaces =
                buildFontFaces(boldFontB64, 700, 800, 900) + buildFontFaces(mediumFontB64, 500, 600);

        htmlTemplate = rawTemplate.replace(GOOGLE_FONTS_IMPORT, fontFaces);
        budgetHtmlTemplate = rawBudgetTemplate.replace(GOOGLE_FONTS_IMPORT, fontFaces);

        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu")));
            available = true;
            log.info("ShareImageRenderer initialized — Chromium ready");
        } catch (Exception e) {
            log.error("Failed to initialize Playwright — OG image generation disabled", e);
            available = false;
        }
    }

    @PreDestroy
    void destroy() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    public synchronized Optional<byte[]> render(ShareData data) {
        if (!available) {
            return Optional.empty();
        }

        String html = buildHtml(data);

        try (Page page = browser.newPage(new Browser.NewPageOptions()
                .setViewportSize(WIDTH_PX, HEIGHT_PX)
                .setDeviceScaleFactor(DEVICE_SCALE_FACTOR))) {
            page.setContent(html, new Page.SetContentOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(RENDER_TIMEOUT_MS));
            return Optional.of(page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(ScreenshotType.PNG)
                    .setTimeout(RENDER_TIMEOUT_MS)));
        }
    }

    public synchronized Optional<byte[]> render(BudgetShareData data) {
        if (!available) {
            return Optional.empty();
        }

        try (Page page = browser.newPage(new Browser.NewPageOptions()
                .setViewportSize(WIDTH_PX, HEIGHT_PX)
                .setDeviceScaleFactor(DEVICE_SCALE_FACTOR))) {
            page.setContent(buildBudgetHtml(data), new Page.SetContentOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(RENDER_TIMEOUT_MS));
            return Optional.of(page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(ScreenshotType.PNG)
                    .setTimeout(RENDER_TIMEOUT_MS)));
        }
    }

    private String buildHtml(ShareData data) {
        List<CountrySquare> squares = data.squares();
        int count = squares.size();

        int tileSize;
        int tileGap;
        if (count <= 3) {
            tileSize = 156;
            tileGap = 32;
        } else if (count <= 5) {
            tileSize = 138;
            tileGap = 26;
        } else {
            tileSize = 100;
            tileGap = 20;
        }

        String tilesHtml =
                squares.stream()
                        .map(sq -> "<div class=\"tile %s\"></div>".formatted(tileClass(sq)))
                        .collect(Collectors.joining("\n"));

        long totalSeconds = data.activeTimeMs() / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        String timeHtml = minutes == 0
                ? "0<span class=\"dim\">:</span>%02d".formatted(seconds)
                : "%d<span class=\"dim\">:</span>%02d".formatted(minutes, seconds);

        String challenge = "Your turn.";

        String streakStat = "";
        if (data.streak() > 0) {
            streakStat = STREAK_STAT_TEMPLATE.formatted(data.streak());
        }

        String level = data.level();

        return htmlTemplate
                .replace("{{DATE}}", data.date().format(DATE_FMT))
                .replace("{{DIFFICULTY}}", formatDifficulty(level).toUpperCase())
                .replace("{{DIFFICULTY_ICON}}", difficultyIcon(level))
                .replace("{{CHALLENGE}}", challenge)
                .replace("{{TIME_HTML}}", timeHtml)
                .replace("{{TILE_SIZE}}", String.valueOf(tileSize))
                .replace("{{TILE_GAP}}", String.valueOf(tileGap))
                .replace("{{TILE_COUNT}}", String.valueOf(count))
                .replace("{{TILES_HTML}}", tilesHtml)
                .replace("{{HINTS}}", data.totalHintsSeen() + "/" + data.totalHintsAvailable())
                .replace("{{XP}}", String.format("%,d", data.totalXpEarned()))
                .replace("{{STREAK_STAT}}", streakStat)
                .replace("{{ACCENT}}", accent(level))
                .replace("{{ACCENT_RGB}}", accentRgb(level))
                .replace("{{GLOW_1}}", glow1(level))
                .replace("{{GLOW_2}}", glow2(level))
                .replace("{{PILL_BORDER}}", pillBorder(level))
                .replace("{{PILL_BG}}", pillBg(level))
                .replace("{{PILL_COLOR}}", pillColor(level))
                .replace("{{PILL_SHADOW}}", pillShadow(level));
    }

    private String buildBudgetHtml(BudgetShareData data) {
        String buildings = data.buildings().stream()
                .map(this::budgetBuildingHtml)
                .collect(Collectors.joining("\n"));

        return budgetHtmlTemplate
                .replace("{{DATE}}", data.date().format(DATE_FMT))
                .replace("{{XP}}", String.format("%,d", data.totalXpEarned()))
                .replace("{{ACCURACY}}", String.format(Locale.US, "%.1f", data.averageAccuracy()))
                .replace("{{BUILDINGS_HTML}}", buildings);
    }

    private String budgetBuildingHtml(BudgetShareBuilding building) {
        double accuracy = Math.clamp(building.accuracyPercent(), 0, 100);
        int height = budgetBuildingHeight(accuracy, building.styleId());
        int windowCount = Math.clamp((height - 24) / 16 * 3, 3, 33);
        String windows = IntStream.range(0, windowCount)
                .mapToObj(index -> "<span class=\"window%s\"></span>".formatted(
                        budgetWindowLit(index, building.styleId(), accuracy) ? "" : " off"))
                .collect(Collectors.joining());

        String roof = switch (building.styleId()) {
            case 0, 3 -> "<span class=\"antenna\"></span>";
            case 4 -> "<span class=\"pipe left\"></span><span class=\"pipe right\"></span>";
            case 5 -> "<span class=\"pipe left\"></span>";
            case 6 -> "<span class=\"dome\"></span>";
            default -> "";
        };

        return """
                <div class="building-wrap" style="height:%dpx">
                  <div class="building %s style-%d" style="height:%dpx">
                    <div class="roof">%s</div>
                    <div class="windows">%s</div>
                  </div>
                  <div class="label"><b>%d%%</b><span>Question %d</span></div>
                </div>
                """.formatted(
                height,
                budgetBuildingClass(accuracy),
                building.styleId(),
                height,
                roof,
                windows,
                Math.round(accuracy),
                building.position());
    }

    private int budgetBuildingHeight(double accuracy, int styleId) {
        double baseHeight = 28 + accuracy * 2.35;
        double factor = switch (styleId) {
            case 0 -> 1.08;
            case 1 -> 0.92;
            case 2 -> 0.82;
            case 3 -> 1.18;
            case 4 -> 0.88;
            case 5 -> 1.02;
            case 6 -> 0.96;
            default -> 1.12;
        };
        int offset = switch (styleId) {
            case 0 -> 10;
            case 1 -> 3;
            case 3 -> 15;
            case 4 -> 6;
            case 5 -> 8;
            case 6 -> 5;
            case 7 -> 12;
            default -> 0;
        };
        return (int) Math.round(baseHeight * factor) + offset;
    }

    private boolean budgetWindowLit(int index, int styleId, double accuracy) {
        double ratio = 0.08 + accuracy * 0.0092;
        double deterministicValue = Math.floorMod(index * 37 + styleId * 19 + 11, 101) / 100.0;
        return deterministicValue < ratio;
    }

    private String budgetBuildingClass(double accuracy) {
        if (accuracy <= 0) return "zero";
        if (accuracy < 30) return "low";
        if (accuracy < 60) return "mid";
        if (accuracy < 85) return "good";
        return "great";
    }

    private String tileClass(CountrySquare square) {
        if (!square.guessed()) return "missed";
        if (square.hintsRevealed() <= 1) return "first";
        if (square.hintsRevealed() == 2) return "try2";
        if (square.hintsRevealed() == 3) return "try3";
        return "try4";
    }

    private static String buildFontFaces(String base64, int... weights) {
        return IntStream.of(weights)
                .mapToObj(w -> FONT_FACE_TEMPLATE.formatted(w, base64))
                .collect(Collectors.joining());
    }

    private static String encodeFont(String path) {
        return Base64.getEncoder().encodeToString(loadResourceBytes(path));
    }

    private static String loadResource() {
        return loadResource(TEMPLATE_PATH);
    }

    private static String loadResource(String path) {
        try (InputStream is =
                     ShareImageRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }

    private static byte[] loadResourceBytes(String path) {
        try (InputStream is =
                     ShareImageRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + path);
            return is.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + path, e);
        }
    }
}
