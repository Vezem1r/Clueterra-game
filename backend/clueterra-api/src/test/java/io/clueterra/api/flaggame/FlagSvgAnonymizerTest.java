package io.clueterra.api.flaggame;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.clueterra.api.flaggame.service.FlagSvgAnonymizer;

class FlagSvgAnonymizerTest {

    private static final String FLAGS = "classpath:assets/flags/*.svg";
    private static final String SVG_SUFFIX = ".svg";
    private static final int MINIMUM_FLAGS = 197;

    private final FlagSvgAnonymizer anonymizer = new FlagSvgAnonymizer();

    @Test
    void noBundledFlagRevealsItsCountryAfterAnonymizing() throws IOException {
        Resource[] flags = new PathMatchingResourcePatternResolver().getResources(FLAGS);

        assertThat(flags).hasSizeGreaterThanOrEqualTo(MINIMUM_FLAGS);
        for (Resource flag : flags) {
            String iso = flag.getFilename().replace(SVG_SUFFIX, "").toLowerCase(Locale.ROOT);
            String original = new String(flag.getContentAsByteArray(), StandardCharsets.UTF_8);
            String anonymized = new String(anonymizer.anonymize(flag.getContentAsByteArray()), StandardCharsets.UTF_8);
            Pattern leak = Pattern.compile("(?i)flag-icons|(?<![a-z0-9])" + Pattern.quote(iso) + "-[a-z0-9]");

            assertThat(leak.matcher(anonymized).find()).as(flag.getFilename()).isFalse();
            assertThat(anonymized.length()).as(flag.getFilename()).isLessThanOrEqualTo(original.length());
            assertThat(anonymized).as(flag.getFilename()).startsWith("<svg");
        }
    }

    @Test
    void renamedIdsKeepTheirReferences() {
        String svg = "<svg id=\"flag-icons-np\"><clipPath id=\"np-a\"/><g clip-path=\"url(#np-a)\"/>"
                + "<use href=\"#np-a\"/></svg>";

        String anonymized = new String(anonymizer.anonymize(svg.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);

        assertThat(anonymized).isEqualTo("<svg id=\"f0\"><clipPath id=\"f1\"/><g clip-path=\"url(#f1)\"/><use href=\"#f1\"/></svg>");
    }

    @Test
    void classNamesAreRenamedInAttributesAndStyles() {
        String svg = "<svg><style>.bo-fil1{fill:red}.bo-str2{stroke:#000}</style>"
                + "<path class=\"bo-fil1 bo-str2\"/><path class=\"bo-fil1\"/></svg>";

        String anonymized = new String(anonymizer.anonymize(svg.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);

        assertThat(anonymized).isEqualTo("<svg><style>.c0{fill:red}.c1{stroke:#000}</style>"
                + "<path class=\"c0 c1\"/><path class=\"c0\"/></svg>");
    }
}
