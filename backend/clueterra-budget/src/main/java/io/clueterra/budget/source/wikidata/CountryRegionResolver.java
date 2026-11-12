package io.clueterra.budget.source.wikidata;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.clueterra.budget.project.CandidateRegion;

@Component
public class CountryRegionResolver {

    private static final Set<String> AFRICA =
            Set.of(
                    "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "DZ", "EG", "EH",
                    "ER", "ET", "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS", "LY", "MA", "MG",
                    "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RW", "SC", "SD", "SL", "SN", "SO", "SS",
                    "ST", "SZ", "TD", "TG", "TN", "TZ", "UG", "ZA", "ZM", "ZW");

    private static final Set<String> ASIA =
            Set.of(
                    "AE", "AF", "AM", "AZ", "BD", "BH", "BN", "BT", "CN", "CY", "GE", "ID", "IL", "IN", "IQ",
                    "IR", "JO", "JP", "KG", "KH", "KP", "KR", "KW", "KZ", "LA", "LB", "LK", "MM", "MN", "MV",
                    "MY", "NP", "OM", "PH", "PK", "PS", "QA", "SA", "SG", "SY", "TH", "TJ", "TL", "TM", "TR",
                    "TW", "UZ", "VN", "YE");

    private static final Set<String> EUROPE =
            Set.of(
                    "AD", "AL", "AT", "BA", "BE", "BG", "BY", "CH", "CZ", "DE", "DK", "EE", "ES", "FI", "FR",
                    "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV", "MC", "MD", "ME", "MK",
                    "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI", "SK", "SM", "UA", "VA", "XK");

    private static final Set<String> NORTH_AMERICA =
            Set.of(
                    "AG", "BB", "BS", "BZ", "CA", "CR", "CU", "DM", "DO", "GD", "GT", "HN", "HT", "JM", "KN",
                    "LC", "MX", "NI", "PA", "SV", "TT", "US", "VC");

    private static final Set<String> SOUTH_AMERICA =
            Set.of("AR", "BO", "BR", "CL", "CO", "EC", "GY", "PE", "PY", "SR", "UY", "VE");

    private static final Set<String> OCEANIA =
            Set.of("AU", "FJ", "FM", "KI", "MH", "NR", "NZ", "PG", "PW", "SB", "TO", "TV", "VU", "WS");

    public CandidateRegion resolve(String countryCode) {
        String normalized = countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT);
        if (AFRICA.contains(normalized)) {
            return CandidateRegion.AFRICA;
        }
        if (ASIA.contains(normalized)) {
            return CandidateRegion.ASIA;
        }
        if (EUROPE.contains(normalized)) {
            return CandidateRegion.EUROPE;
        }
        if (NORTH_AMERICA.contains(normalized)) {
            return CandidateRegion.NORTH_AMERICA;
        }
        if (SOUTH_AMERICA.contains(normalized)) {
            return CandidateRegion.SOUTH_AMERICA;
        }
        if (OCEANIA.contains(normalized)) {
            return CandidateRegion.OCEANIA;
        }
        return CandidateRegion.UNKNOWN;
    }
}
