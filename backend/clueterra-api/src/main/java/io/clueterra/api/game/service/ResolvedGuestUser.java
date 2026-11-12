package io.clueterra.api.game.service;

record ResolvedGuestUser(
        long userId,
        boolean created,
        String termsVersionAccepted,
        String privacyVersionAccepted) {
}
