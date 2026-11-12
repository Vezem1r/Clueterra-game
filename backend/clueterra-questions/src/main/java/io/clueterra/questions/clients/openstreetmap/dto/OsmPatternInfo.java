package io.clueterra.questions.clients.openstreetmap.dto;

import java.util.concurrent.atomic.AtomicLong;

public record OsmPatternInfo(String exampleName, String exampleId, AtomicLong count) {

    public OsmPatternInfo(String name, String id) {
        this(name, id, new AtomicLong(1));
    }
}
