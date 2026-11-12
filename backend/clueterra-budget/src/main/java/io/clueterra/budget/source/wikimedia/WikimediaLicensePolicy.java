package io.clueterra.budget.source.wikimedia;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import okhttp3.HttpUrl;

@Component
public class WikimediaLicensePolicy {

    private static final Pattern ATTRIBUTION_LICENSE_LABEL =
            Pattern.compile("^CC BY(?: SA)? [0-9]+(?:\\.[0-9]+)?(?: [A-Z]{2,3})?$");

    public boolean isAllowed(String label, String url) {
        return isAllowed(label, url, "");
    }

    public boolean isAllowed(String label, String url, String licenseCode) {
        String normalized =
                (label == null ? "" : label).toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        HttpUrl licenseUrl = HttpUrl.parse(url);
        if (licenseUrl == null || !licenseUrl.isHttps()) {
            return false;
        }

        boolean creativeCommonsHost = "creativecommons.org".equalsIgnoreCase(licenseUrl.host());
        String path = licenseUrl.encodedPath().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("CC0")) {
            return creativeCommonsHost && path.startsWith("/publicdomain/zero/");
        }
        if (normalized.contains("PUBLIC DOMAIN")) {
            return (creativeCommonsHost && path.startsWith("/publicdomain/"))
                    || ("pd".equalsIgnoreCase(licenseCode)
                    && "commons.wikimedia.org".equalsIgnoreCase(licenseUrl.host()));
        }
        return creativeCommonsHost
                && ATTRIBUTION_LICENSE_LABEL.matcher(normalized).matches()
                && (path.startsWith("/licenses/by/") || path.startsWith("/licenses/by-sa/"));
    }
}
