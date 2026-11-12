package io.clueterra.api.flaggame.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class FlagSvgAnonymizer {

    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]+)\"");
    private static final Pattern CLASS = Pattern.compile("\\bclass=\"([^\"]+)\"");
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<(title|desc|metadata)\\b.*?</\\1>", Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String ID_PREFIX = "f";
    private static final String CLASS_PREFIX = "c";
    private static final String NOT_NAME_CHARACTER = "(?![\\w-])";

    public byte[] anonymize(byte[] svg) {
        String text = TITLE.matcher(COMMENT.matcher(new String(svg, StandardCharsets.UTF_8)).replaceAll("")).replaceAll("");
        return renameClasses(renameIds(text)).getBytes(StandardCharsets.UTF_8);
    }

    private String renameIds(String text) {
        Map<String, String> renamed = new LinkedHashMap<>();
        Matcher ids = ID.matcher(text);
        while (ids.find()) {
            renamed.putIfAbsent(ids.group(1), ID_PREFIX + renamed.size());
        }
        String result = text;
        for (Map.Entry<String, String> id : renamed.entrySet()) {
            String original = Pattern.quote(id.getKey());
            result = result.replaceAll("\\bid=\"" + original + "\"", "id=\"" + id.getValue() + "\"")
                    .replaceAll("#" + original + "(?=[\")'\\s])", "#" + id.getValue());
        }
        return result;
    }

    private String renameClasses(String text) {
        Map<String, String> renamed = new LinkedHashMap<>();
        Matcher classes = CLASS.matcher(text);
        while (classes.find()) {
            WHITESPACE.splitAsStream(classes.group(1).trim())
                    .forEach(name -> renamed.putIfAbsent(name, CLASS_PREFIX + renamed.size()));
        }
        Matcher attributes = CLASS.matcher(text);
        StringBuilder rewritten = new StringBuilder();
        while (attributes.find()) {
            String names = Arrays.stream(WHITESPACE.split(attributes.group(1).trim()))
                    .map(renamed::get)
                    .collect(Collectors.joining(" "));
            attributes.appendReplacement(rewritten, Matcher.quoteReplacement("class=\"" + names + "\""));
        }
        attributes.appendTail(rewritten);
        String result = rewritten.toString();
        for (Map.Entry<String, String> name : renamed.entrySet()) {
            result = result.replaceAll("\\." + Pattern.quote(name.getKey()) + NOT_NAME_CHARACTER, "." + name.getValue());
        }
        return result;
    }
}
