package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlagAssetServiceTest {

  private FlagAssetService service;

  @BeforeEach
  void setUp() {
    service = new FlagAssetService();
  }

  @Test
  @DisplayName("fetchFlag loads bundled SVG assets for standard ISO codes")
  void fetchFlagLoadsBundledSvgAsset() {
    FlagAssetPayload payload = service.fetchFlag("DE");

    assertThat(payload.contentType()).isEqualTo("image/svg+xml");
    assertThat(new String(payload.bytes())).contains("<svg");
  }

  @Test
  @DisplayName("fetchFlags returns each known flag once, keyed by lower-case code, and skips unknown codes")
  void fetchFlagsBundlesKnownFlags() {
    Map<String, String> flags = service.fetchFlags(List.of("DE", "fr", "de", "zz"));

    assertThat(flags).containsOnlyKeys("de", "fr");
    assertThat(flags.get("fr")).startsWith("<svg");
  }
}
