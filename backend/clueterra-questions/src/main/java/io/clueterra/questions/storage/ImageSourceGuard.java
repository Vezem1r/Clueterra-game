package io.clueterra.questions.storage;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class ImageSourceGuard {

    private ImageSourceGuard() {
    }

    static void requirePublicHttpUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Unsupported image URL scheme: " + uri.getScheme());
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("Image URL host is not allowed: " + host);
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Image URL resolves to a non-public address: " + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Image URL host cannot be resolved: " + host, e);
        }
    }
}
