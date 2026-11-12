package io.clueterra.questions.clients.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImageRightsSupportTest {

    @Test
    void isCommerciallySafeOpenLicense_allowsCommercialCreativeCommonsFamilies() {
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("CC BY-SA 4.0")).isTrue();
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("http://creativecommons.org/licenses/by/2.0/")).isTrue();
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("http://creativecommons.org/publicdomain/zero/1.0/")).isTrue();
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("PD-old-70")).isTrue();
    }

    @Test
    void isCommerciallySafeOpenLicense_rejectsRestrictedLicenses() {
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("CC BY-NC 4.0")).isFalse();
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("GNU Free Documentation License")).isFalse();
        assertThat(ImageRightsSupport.isCommerciallySafeOpenLicense("All rights reserved")).isFalse();
    }

    @Test
    void publicDomainGateRejectsOrdinaryCreativeCommonsCurrencyPhotos() {
        assertThat(ImageRightsSupport.isPublicDomainOrCc0License("CC BY-SA 4.0")).isFalse();
        assertThat(ImageRightsSupport.isPublicDomainOrCc0License("CC0 1.0")).isTrue();
        assertThat(ImageRightsSupport.isPublicDomainOrCc0License("PD-old-70")).isTrue();
    }

    @Test
    void licenseLabel_normalizesCreativeCommonsUrls() {
        assertThat(ImageRightsSupport.licenseLabel("http://creativecommons.org/licenses/by-sa/2.0/"))
                .isEqualTo("CC BY-SA 2.0");
        assertThat(ImageRightsSupport.licenseLabel("http://creativecommons.org/publicdomain/zero/1.0/"))
                .isEqualTo("CC0 1.0");
        assertThat(ImageRightsSupport.licenseLabel("PD-old-70"))
                .isEqualTo("Public domain");
    }

    @Test
    void wikimediaAuthorName_cleansCommonCommonsBoilerplate() {
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "Flag_of_Åland.svg: Gabbe derivative work: NikNaks93 (talk)",
                null))
                .isEqualTo("Gabbe; NikNaks93");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "David Cross - User: (WT-shared) Davidx at wts wikivoyage",
                null))
                .isEqualTo("David Cross; Davidx");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "(en.wiki: \"Taken and donated by w:User:John.)",
                null))
                .isEqualTo("John");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "Diliff derivative work: Crop by Diliff",
                null))
                .isEqualTo("Diliff");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "created by <a href=\"https://en.wikipedia.org/wiki/User:Zscout370\">Zscout370</a>",
                null))
                .isEqualTo("Zscout370");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "User:Charismaniac",
                null))
                .isEqualTo("Charismaniac");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "Achim Raschka (talk)",
                null))
                .isEqualTo("Achim Raschka");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "Vberger at French Wikipedia",
                null))
                .isEqualTo("Vberger");
    }

    @Test
    void wikimediaAuthorName_dropsPlaceholderAuthorsAndFallsBackToUsefulCredit() {
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "No machine-readable author provided. Shula assumed (based on copyright claims).",
                null))
                .isNull();
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                "Unknown authorUnknown author",
                null))
                .isNull();
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                null,
                "Collectie Stichting Nationaal Museum van Wereldculturen"))
                .isEqualTo("Collectie Stichting Nationaal Museum van Wereldculturen");
        assertThat(ImageRightsSupport.wikimediaAuthorName(
                null,
                "<span class=\"int-own-work\" lang=\"en\">Own work</span>"))
                .isNull();
    }

    @Test
    void wikimediaAttributionText_usesCleanedBylineWhenAvailable() {
        assertThat(ImageRightsSupport.wikimediaAttributionText(
                "Diliff",
                "<span class=\"int-own-work\" lang=\"en\">Own work</span>"))
                .isEqualTo("Image by Diliff via Wikimedia Commons.");
        assertThat(ImageRightsSupport.wikimediaAttributionText(
                null,
                "Collectie Stichting Nationaal Museum van Wereldculturen"))
                .isEqualTo("Image by Collectie Stichting Nationaal Museum van Wereldculturen via Wikimedia Commons.");
        assertThat(ImageRightsSupport.wikimediaAttributionText(
                null,
                "<span class=\"int-own-work\" lang=\"en\">Own work</span>"))
                .isEqualTo("Image via Wikimedia Commons.");
    }
}
