package br.com.sylph.service;

import java.util.List;
import java.util.regex.Pattern;

/** Resolves stable chunk IDs only within their exact retrieved source pages. */
public final class StableCitations {
    private StableCitations() { }
    private static final Pattern REF = Pattern.compile("\\[Trecho:\\s*([^\\]|]+)\\s*\\|\\s*Página:\\s*(\\d+)\\s*\\]");

    public static String resolve(String answer, List<RagService.Source> sources) {
        var matcher = REF.matcher(answer);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = matcher.group(1).trim();
            int page;
            try { page = Integer.parseInt(matcher.group(2)); }
            catch (NumberFormatException ex) { page = -1; }
            final int number = page;
            var matches = sources.stream().filter(s -> s.pagina() == number && s.chunkIds().contains(id)).toList();
            // Unknown/ambiguous IDs remain unrecognized and are rejected by the existing parser.
            String replacement = matches.size() == 1 ? "[Fonte: " + matches.getFirst().documento() + " | Página: " + page + "]" : matcher.group();
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
