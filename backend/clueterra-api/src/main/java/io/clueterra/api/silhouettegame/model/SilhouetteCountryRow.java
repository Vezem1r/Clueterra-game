package io.clueterra.api.silhouettegame.model;

public record SilhouetteCountryRow(
        String iso2,
        String subregion,
        String names,
        String facts,
        String flagColors,
        double centerLng,
        double centerLat,
        String bounds,
        String shapePath,
        SilhouettePhotoRow photo) {
}
