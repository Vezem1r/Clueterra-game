package io.clueterra.api.assets.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlagAssetProvenanceTest {

  private static final Path FLAGS_DIRECTORY =
      Path.of("src", "main", "resources", "assets", "flags");

  @Test
  @DisplayName("bundled flag assets have pinned provenance and license evidence")
  void bundledFlagAssetsHavePinnedProvenanceAndLicenseEvidence() throws IOException {
    Path provenance = FLAGS_DIRECTORY.resolve("PROVENANCE.yml");
    Path license = FLAGS_DIRECTORY.resolve("LICENSE.flag-icons");

    assertThat(provenance).isRegularFile();
    assertThat(license).isRegularFile();
    assertThat(Files.readString(provenance))
        .contains(
            "https://github.com/lipis/flag-icons",
            "v7.5.0",
            "7aa5b2bdddd570ece62c812c0cb588ccdc099e2e",
            "license: \"MIT\"",
            "obtained_at: \"2026-09-06\"",
            "local_modifications: \"none\"");

    try (Stream<Path> files = Files.list(FLAGS_DIRECTORY)) {
      List<String> svgFiles =
          files
              .filter(path -> path.toString().endsWith(".svg"))
              .map(path -> path.getFileName().toString())
              .toList();

      assertThat(svgFiles).hasSize(250).doesNotContain("jg.svg");
    }
  }
}
