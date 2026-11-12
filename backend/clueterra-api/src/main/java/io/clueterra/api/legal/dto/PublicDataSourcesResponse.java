package io.clueterra.api.legal.dto;

import java.util.List;

public record PublicDataSourcesResponse(
        String registryVersion,
        List<PublicDataSourceItemResponse> sources) {
}
