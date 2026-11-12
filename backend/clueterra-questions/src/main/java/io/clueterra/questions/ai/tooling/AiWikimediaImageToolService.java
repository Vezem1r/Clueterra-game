package io.clueterra.questions.ai.tooling;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.clueterra.questions.ai.service.generation.AiResolvedWikimediaImage;
import io.clueterra.questions.ai.service.generation.AiWikimediaImageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiWikimediaImageToolService {

    private final AiWikimediaImageResolver imageResolver;

    @Tool("""
            Searches Wikimedia Commons for a real, downloadable, commercially safe image.
            Use this before returning any imageSourceUrl. Never invent Wikimedia file URLs.
            The result contains the exact imageSourceUrl to copy into the JSON proposal.
            """)
    public String searchWikimediaImage(
            @P("Search terms for the desired image subject, e.g. 'Mir Castle Complex Belarus'") String query) {
        return formatResult(query, imageResolver.search(query));
    }

    @Tool("""
            Searches Wikimedia Commons specifically for a banknote or coin image with an explicit
            Public Domain or CC0 status. Use this tool for every CURRENCY image proposal; the general
            Wikimedia search is not sufficient for currency designs.
            The image still requires the later Gemini visual gate before it can reach approval.
            """)
    public String searchPublicDomainCurrencyImage(
            @P("Precise currency search terms including country, object type and useful motif") String query) {
        return formatResult(query, imageResolver.searchPublicDomain(query));
    }

    private String formatResult(String query, Optional<AiResolvedWikimediaImage> image) {
        if (image.isEmpty()) {
            return "No commercially safe Wikimedia image found for query: " + query;
        }

        AiResolvedWikimediaImage resolved = image.get();
        return """
                WIKIMEDIA IMAGE FOUND
                title=%s
                imageSourceUrl=%s
                sourcePageUrl=%s
                license=%s
                licenseUrl=%s
                attribution=%s
                """
                .formatted(
                        resolved.title(),
                        resolved.imageUrl(),
                        resolved.sourcePageUrl(),
                        resolved.licenseLabel(),
                        resolved.licenseUrl(),
                        resolved.attributionText());
    }
}
