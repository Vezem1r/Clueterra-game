package io.clueterra.questions.clients.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("java:S1192")
public final class CountryReference {

    private CountryReference() {
    }

    public static final Set<String> REQUIRES_THE = Set.of(
            "United States", "United Kingdom", "Netherlands", "Philippines",
            "Czech Republic", "Dominican Republic", "Central African Republic",
            "Democratic Republic of the Congo", "Republic of the Congo",
            "United Arab Emirates", "Bahamas", "Gambia", "Maldives",
            "Marshall Islands", "Solomon Islands", "Comoros", "Seychelles",
            "Federated States of Micronesia", "Republic of Ireland"
    );

    private static final Map<String, String> DISPLAY_NAME_OVERRIDES = Map.of(
            "CZ", "Czech Republic",
            "CD", "Democratic Republic of the Congo",
            "FM", "Federated States of Micronesia",
            "TL", "East Timor"
    );

    public static final Map<String, String> COUNTRY_NAMES = createCountryNames();
    public static final Map<String, String> NAME_TO_CODE = createNameToCode();
    public static final Map<String, String> ADJECTIVE_FORMS = createAdjectiveForms();
    public static final Map<String, List<String>> NAME_VARIATIONS = createNameVariations();

    private static final Pattern COUNTRY_PATTERN = createCountryPattern();

    public static String getCountryName(String code) {
        return COUNTRY_NAMES.get(code);
    }

    public static String getCodeByName(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        String code = NAME_TO_CODE.get(name);
        if (StringUtils.isNotBlank(code)) {
            return code;
        }

        String lower = name.toLowerCase().trim();
        for (var entry : NAME_TO_CODE.entrySet()) {
            if (entry.getKey().toLowerCase().equals(lower)) {
                return entry.getValue();
            }
        }

        for (var entry : COUNTRY_NAMES.entrySet()) {
            String countryName = entry.getValue();
            if (countryName.toLowerCase().equals(lower)) {
                return entry.getKey();
            }
        }

        if (name.length() == 2 || name.length() == 3) {
            String upper = name.toUpperCase();
            if (COUNTRY_NAMES.containsKey(upper)) {
                return upper;
            }
        }

        return null;
    }

    public static boolean needsThe(String countryName) {
        return REQUIRES_THE.contains(countryName);
    }

    public static List<String> getNameVariations(String countryName) {
        List<String> variations = NAME_VARIATIONS.get(countryName);
        if (variations != null) {
            return variations;
        }

        List<String> defaults = new ArrayList<>();
        defaults.add(countryName);
        String adj = ADJECTIVE_FORMS.get(countryName);
        if (StringUtils.isNotBlank(adj)) {
            defaults.add(adj);
        }
        return defaults;
    }

    public static Set<String> getAllCountryCodes() {
        return COUNTRY_NAMES.keySet();
    }

    public static Set<String> getAllCountryNames() {
        return new HashSet<>(COUNTRY_NAMES.values());
    }

    public static Pattern getCountryPattern() {
        return COUNTRY_PATTERN;
    }

    private static Pattern createCountryPattern() {
        Set<String> allCountryNames = new HashSet<>();
        allCountryNames.addAll(getAllCountryNames());
        allCountryNames.addAll(NAME_TO_CODE.keySet());
        allCountryNames.addAll(ADJECTIVE_FORMS.values());
        for (List<String> variations : NAME_VARIATIONS.values()) {
            allCountryNames.addAll(variations);
        }

        allCountryNames.remove(null);
        allCountryNames.remove("");

        List<String> sortedCountries = allCountryNames.stream()
                .filter(name -> name != null && !name.isEmpty())
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .map(CountryReference::escapeRegexSpecialChars)
                .toList();

        String patternStr = "(?:^|,\\s*|\\()(" + String.join("|", sortedCountries) + ")(?:$|[,\\s)])";
        return Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
    }

    private static String escapeRegexSpecialChars(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }

        return input
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("|", "\\|")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static Map<String, String> createCountryNames() {
        Map<String, String> map = new HashMap<>();
        try (InputStream is = CountryReference.class.getResourceAsStream("/mappings/country-reference.yml")) {
            ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
            JsonNode root = yaml.readTree(is);
            for (JsonNode entry : root.path("countries")) {
                String iso2 = entry.path("iso2").asText("");
                String name = entry.path("name").asText("");
                if (!iso2.isBlank() && !name.isBlank()) {
                    map.put(iso2, DISPLAY_NAME_OVERRIDES.getOrDefault(iso2, name));
                }
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to load country-reference.yml: " + e.getMessage());
        }
        return Map.copyOf(map);
    }

    private static Map<String, String> createNameToCode() {
        Map<String, String> map = new HashMap<>();

        for (var entry : COUNTRY_NAMES.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }

        map.put("USA", "US");
        map.put("U.S.", "US");
        map.put("U.S.A.", "US");
        map.put("US", "US");
        map.put("America", "US");
        map.put("UK", "GB");
        map.put("U.K.", "GB");
        map.put("Britain", "GB");
        map.put("Great Britain", "GB");
        map.put("England", "GB");
        map.put("Holland", "NL");
        map.put("The Netherlands", "NL");
        map.put("Korea, South", "KR");
        map.put("Korea, North", "KP");
        map.put("Republic of Korea", "KR");
        map.put("DPRK", "KP");
        map.put("Czechia", "CZ");
        map.put("Czech", "CZ");
        map.put("PRC", "CN");
        map.put("People's Republic of China", "CN");
        map.put("ROC", "TW");
        map.put("Republic of China", "TW");
        map.put("Burma", "MM");
        map.put("Persia", "IR");
        map.put("UAE", "AE");
        map.put("Emirates", "AE");
        map.put("DRC", "CD");
        map.put("Congo, Democratic Republic", "CD");
        map.put("Congo, Republic", "CG");
        map.put("Côte d'Ivoire", "CI");
        map.put("Cote d'Ivoire", "CI");
        map.put("Eswatini", "SZ");
        map.put("Swaziland", "SZ");
        map.put("Timor-Leste", "TL");
        map.put("East Timor", "TL");
        map.put("Vatican", "VA");
        map.put("Holy See", "VA");
        map.put("USSR", "RU");
        map.put("Soviet Union", "RU");
        map.put("Soviet Russia", "RU");
        map.put("China,_People's_Republic_of", "CN");
        map.put("China,_Republic_of", "TW");
        map.put("Congo,_Democratic_Republic", "CD");
        map.put("Costa_Rica", "CR");
        map.put("Czech_Republic", "CZ");
        map.put("East_Timor", "TL");
        map.put("Faroe_Islands", "FO");
        map.put("Myanmar_(Burma)", "MM");
        map.put("New_Zealand", "NZ");
        map.put("North_Macedonia", "MK");
        map.put("Russia_and_Soviet_Union", "RU");
        map.put("Saint_Lucia", "LC");
        map.put("Saudi_Arabia", "SA");
        map.put("South_Africa", "ZA");
        map.put("South_Korea", "KR");
        map.put("Trinidad_and_Tobago", "TT");
        map.put("United_Kingdom", "GB");
        map.put("United_States", "US");
        map.put("Yugoslavia", "RS");

        return map;
    }

    private static Map<String, String> createAdjectiveForms() {
        Map<String, String> map = new HashMap<>();

        map.put("United States", "American");
        map.put("United Kingdom", "British");
        map.put("France", "French");
        map.put("Germany", "German");
        map.put("Japan", "Japanese");
        map.put("China", "Chinese");
        map.put("Russia", "Russian");
        map.put("Brazil", "Brazilian");
        map.put("India", "Indian");
        map.put("Australia", "Australian");
        map.put("Italy", "Italian");
        map.put("Spain", "Spanish");
        map.put("Mexico", "Mexican");
        map.put("South Korea", "Korean");
        map.put("North Korea", "Korean");
        map.put("Turkey", "Turkish");
        map.put("Thailand", "Thai");
        map.put("Vietnam", "Vietnamese");
        map.put("Greece", "Greek");
        map.put("Poland", "Polish");
        map.put("Sweden", "Swedish");
        map.put("Netherlands", "Dutch");
        map.put("Portugal", "Portuguese");
        map.put("Switzerland", "Swiss");
        map.put("Austria", "Austrian");
        map.put("Belgium", "Belgian");
        map.put("Denmark", "Danish");
        map.put("Norway", "Norwegian");
        map.put("Finland", "Finnish");
        map.put("Ireland", "Irish");
        map.put("Philippines", "Filipino");
        map.put("Liechtenstein", "Liechtensteiner");
        map.put("Indonesia", "Indonesian");
        map.put("Malaysia", "Malaysian");
        map.put("Egypt", "Egyptian");
        map.put("Morocco", "Moroccan");
        map.put("Nigeria", "Nigerian");
        map.put("Ethiopia", "Ethiopian");
        map.put("Peru", "Peruvian");
        map.put("Argentina", "Argentine");
        map.put("Colombia", "Colombian");
        map.put("Chile", "Chilean");
        map.put("Czech Republic", "Czech");
        map.put("Hungary", "Hungarian");
        map.put("Romania", "Romanian");
        map.put("Ukraine", "Ukrainian");
        map.put("Israel", "Israeli");
        map.put("Saudi Arabia", "Saudi");
        map.put("Iran", "Iranian");
        map.put("Iraq", "Iraqi");
        map.put("Pakistan", "Pakistani");
        map.put("Bangladesh", "Bangladeshi");
        map.put("Afghanistan", "Afghan");
        map.put("Canada", "Canadian");
        map.put("New Zealand", "New Zealand");
        map.put("South Africa", "South African");
        map.put("Kenya", "Kenyan");
        map.put("Ghana", "Ghanaian");
        map.put("Tanzania", "Tanzanian");
        map.put("Singapore", "Singaporean");
        map.put("Iceland", "Icelandic");
        map.put("Croatia", "Croatian");
        map.put("Serbia", "Serbian");
        map.put("Bulgaria", "Bulgarian");
        map.put("Slovakia", "Slovak");
        map.put("Slovenia", "Slovenian");
        map.put("Estonia", "Estonian");
        map.put("Latvia", "Latvian");
        map.put("Lithuania", "Lithuanian");
        map.put("Belarus", "Belarusian");
        map.put("Georgia", "Georgian");
        map.put("Armenia", "Armenian");
        map.put("Azerbaijan", "Azerbaijani");
        map.put("Kazakhstan", "Kazakh");
        map.put("Uzbekistan", "Uzbek");
        map.put("Mongolia", "Mongolian");
        map.put("Nepal", "Nepali");
        map.put("Sri Lanka", "Sri Lankan");
        map.put("Myanmar", "Burmese");
        map.put("Cambodia", "Cambodian");
        map.put("Laos", "Lao");
        map.put("Cuba", "Cuban");
        map.put("Jamaica", "Jamaican");
        map.put("Haiti", "Haitian");
        map.put("Venezuela", "Venezuelan");
        map.put("Ecuador", "Ecuadorian");
        map.put("Bolivia", "Bolivian");
        map.put("Paraguay", "Paraguayan");
        map.put("Uruguay", "Uruguayan");

        return Map.copyOf(map);
    }

    private static Map<String, List<String>> createNameVariations() {
        Map<String, List<String>> map = new HashMap<>();

        map.put("United States", List.of("United States", "U.S.", "US", "USA", "America", "American", "Americans"));
        map.put("United Kingdom", List.of("United Kingdom", "UK", "U.K.", "Britain", "British", "England", "English", "Great Britain"));
        map.put("Russia", List.of("Russia", "Russian", "Russians", "USSR", "Soviet Union", "Soviet Russia"));
        map.put("China", List.of("China", "Chinese", "PRC"));
        map.put("South Korea", List.of("South Korea", "Korea", "Korean", "Koreans", "Republic of Korea"));
        map.put("North Korea", List.of("North Korea", "Korea", "Korean", "DPRK"));
        map.put("Germany", List.of("Germany", "German", "Germans", "Deutschland"));
        map.put("France", List.of("France", "French", "Frenchman", "Frenchmen"));
        map.put("Japan", List.of("Japan", "Japanese"));
        map.put("Brazil", List.of("Brazil", "Brazilian", "Brazilians"));
        map.put("India", List.of("India", "Indian", "Indians"));
        map.put("Australia", List.of("Australia", "Australian", "Australians", "Aussie"));
        map.put("Canada", List.of("Canada", "Canadian", "Canadians"));
        map.put("Italy", List.of("Italy", "Italian", "Italians"));
        map.put("Spain", List.of("Spain", "Spanish", "Spaniard", "Spaniards"));
        map.put("Mexico", List.of("Mexico", "Mexican", "Mexicans"));
        map.put("Netherlands", List.of("Netherlands", "Dutch", "Holland", "The Netherlands"));
        map.put("Switzerland", List.of("Switzerland", "Swiss"));
        map.put("New Zealand", List.of("New Zealand", "Kiwi", "Kiwis"));
        map.put("South Africa", List.of("South Africa", "South African", "Mzansi"));
        map.put("American Samoa", List.of("American Samoa", "Amerika Samoa", "Samoan"));
        map.put("Guam", List.of("Guam", "Guamanian", "Guamanians"));
        map.put("Kyrgyzstan", List.of("Kyrgyzstan", "Kyrgyz Republic", "Kyrgyz"));
        map.put("Lebanon", List.of("Lebanon", "Lebanese"));
        map.put("Chad", List.of("Chad", "Chadian", "Tchad", "Tchadienne"));
        map.put("Taiwan", List.of("Taiwan", "Taiwanese", "Republic of China", "ROC"));
        map.put("Monaco", List.of("Monaco", "Monegasque", "Monégasque"));
        map.put("Saudi Arabia", List.of("Saudi Arabia", "Saudi", "Saudis"));
        map.put("United Arab Emirates", List.of("United Arab Emirates", "UAE", "Emirati", "Emirates"));
        map.put("Czech Republic", List.of("Czech Republic", "Czechia", "Czech"));
        map.put("Philippines", List.of("Philippines", "Philippine", "Filipino"));
        map.put("Saint Vincent and the Grenadines", List.of(
                "Saint Vincent and the Grenadines", "Saint Vincent", "St Vincent", "Vincentian"));
        map.put("Myanmar", List.of("Myanmar", "Burma", "Burmese"));
        map.put("Iran", List.of("Iran", "Iranian", "Persia", "Persian"));
        map.put("Swaziland", List.of("Swaziland", "Eswatini"));
        map.put("Ivory Coast", List.of("Ivory Coast", "Côte d'Ivoire", "Cote d'Ivoire"));
        map.put("East Timor", List.of("East Timor", "Timor-Leste"));
        map.put("Vatican City", List.of("Vatican City", "Vatican", "Holy See"));

        return Map.copyOf(map);
    }
}
