package io.clueterra.api.share.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShareCardConstants {

    public static final int WIDTH_PX = 1200;
    public static final int HEIGHT_PX = 630;
    public static final int RENDER_TIMEOUT_MS = 8000;
    public static final int DEVICE_SCALE_FACTOR = 2;

    public static final String TEMPLATE_PATH = "templates/share-card.html";
    public static final String BUDGET_TEMPLATE_PATH = "templates/budget-share-card.html";
    public static final String BOLD_FONT_PATH = "fonts/Inter-Bold.ttf";
    public static final String MEDIUM_FONT_PATH = "fonts/Inter-Medium.ttf";

    public static final String GOOGLE_FONTS_IMPORT =
            "@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap');";

    public static final String ICON_GLOBE =
            """
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/>
            <path d="M2 12h20"/></svg>""";

    public static final String ICON_ZAP =
            """
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0
             13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11
             14z"/></svg>""";

    public static final String ICON_FLAME =
            """
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054
             2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294
             1-3a2.5 2.5 0 0 0 2.5 2.5z"/></svg>""";

    public static final String STREAK_STAT_TEMPLATE =
            """
            <span class="dot">·</span>
            <span><span class="val">%d</span> streak</span>""";

    public static final String FONT_FACE_TEMPLATE =
            """
            @font-face { font-family: 'Inter'; font-weight: %d; font-style: normal;
             src: url(data:font/truetype;base64,%s) format('truetype'); }
            """;

    public static String difficultyIcon(String level) {
        return switch (level) {
            case "EASY" -> ICON_GLOBE;
            case "MEDIUM" -> ICON_ZAP;
            case "HARD" -> ICON_FLAME;
            default -> ICON_GLOBE;
        };
    }

    public static String formatDifficulty(String level) {
        return switch (level) {
            case "EASY" -> "Easy";
            case "MEDIUM" -> "Medium";
            case "HARD" -> "Hard";
            default -> level;
        };
    }

    public static String accent(String level) {
        return switch (level) {
            case "EASY" -> "#2ef0bd";
            case "MEDIUM" -> "#ffc43d";
            case "HARD" -> "#ff4357";
            default -> "#2ef0bd";
        };
    }

    public static String accentRgb(String level) {
        return switch (level) {
            case "EASY" -> "46, 240, 189";
            case "MEDIUM" -> "255, 196, 61";
            case "HARD" -> "255, 67, 87";
            default -> "46, 240, 189";
        };
    }

    public static String glow1(String level) {
        return switch (level) {
            case "EASY" -> "rgba(40, 245, 184, .28)";
            case "MEDIUM" -> "rgba(255, 196, 61, .28)";
            case "HARD" -> "rgba(255, 67, 87, .28)";
            default -> "rgba(40, 245, 184, .28)";
        };
    }

    public static String glow2(String level) {
        return switch (level) {
            case "EASY" -> "rgba(255, 185, 45, .33)";
            case "MEDIUM" -> "rgba(255, 140, 20, .33)";
            case "HARD" -> "rgba(255, 100, 60, .33)";
            default -> "rgba(255, 185, 45, .33)";
        };
    }

    public static String pillBorder(String level) {
        return switch (level) {
            case "EASY" -> "rgba(110, 231, 183, 0.28)";
            case "MEDIUM" -> "rgba(253, 230, 138, 0.28)";
            case "HARD" -> "rgba(253, 164, 175, 0.28)";
            default -> "rgba(110, 231, 183, 0.28)";
        };
    }

    public static String pillBg(String level) {
        return switch (level) {
            case "EASY" -> "linear-gradient(135deg, rgba(16, 185, 129, 0.26), rgba(6, 95, 70, 0.12))";
            case "MEDIUM" ->
                    "linear-gradient(135deg, rgba(245, 158, 11, 0.28), rgba(146, 64, 14, 0.14))";
            case "HARD" ->
                    "linear-gradient(135deg, rgba(244, 63, 94, 0.28), rgba(136, 19, 55, 0.14))";
            default -> "linear-gradient(135deg, rgba(16, 185, 129, 0.26), rgba(6, 95, 70, 0.12))";
        };
    }

    public static String pillColor(String level) {
        return switch (level) {
            case "EASY" -> "#d1fae5";
            case "MEDIUM" -> "#fef3c7";
            case "HARD" -> "#ffe4e6";
            default -> "#d1fae5";
        };
    }

    public static String pillShadow(String level) {
        return switch (level) {
            case "EASY" -> "0 14px 32px rgba(6, 95, 70, 0.24)";
            case "MEDIUM" -> "0 14px 32px rgba(120, 53, 15, 0.24)";
            case "HARD" -> "0 14px 32px rgba(136, 19, 55, 0.24)";
            default -> "0 14px 32px rgba(6, 95, 70, 0.24)";
        };
    }
}
