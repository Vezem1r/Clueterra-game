package io.clueterra.api.admin.hints.image;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WikimediaLicensePolicy {

    private static final Pattern ATTRIBUTION_LICENSE_LABEL =
            Pattern.compile("^CC BY(?: SA)? [0-9]+(?:\\.[0-9]+)?(?: [A-Z]{2,3})?$");

    public boolean isAllowed(String label, String url, String licenseCode) {
        String normalized = label == null
                ? ""
                : label.toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        URI licenseUri = parseUri(url);
        if (licenseUri == null || !"https".equalsIgnoreCase(licenseUri.getScheme())) {
            return false;
        }

        String host = licenseUri.getHost();
        String path = licenseUri.getPath() == null ? "" : licenseUri.getPath().toLowerCase(Locale.ROOT);
        boolean creativeCommonsHost = "creativecommons.org".equalsIgnoreCase(host);
        if (normalized.startsWith("CC0")) {
            return creativeCommonsHost && path.startsWith("/publicdomain/zero/");
        }
        if (normalized.contains("PUBLIC DOMAIN")) {
            return (creativeCommonsHost && path.startsWith("/publicdomain/"))
                    || ("pd".equalsIgnoreCase(licenseCode)
                    && "commons.wikimedia.org".equalsIgnoreCase(host));
        }
        return creativeCommonsHost
                && ATTRIBUTION_LICENSE_LABEL.matcher(normalized).matches()
                && (path.startsWith("/licenses/by/") || path.startsWith("/licenses/by-sa/"));
    }

    private static URI parseUri(String value) {
        try {
            return value == null || value.isBlank() ? null : URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
