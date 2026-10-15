package br.com.sylph.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative lexical checks complement literal quotations and responsibility checks. */
final class ClaimGrounding {
    private ClaimGrounding() { }
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}\\d])\\d+(?:[.,:/-]\\d+)*(?![\\p{L}\\d])");
    private static final Pattern NEGATION=Pattern.compile("(?iu)\\b(?:n[aã]o|nunca|jamais|vedad[oa]s?|proibid[oa]s?|exceto|sem|nem)\\b");
    static List<String> unsupported(String claim,List<RagService.Source> sources) {
        String text=claim.replaceFirst("^\\s*\\d+[.)]\\s*","");
        String evidence=String.join(" ",sources.stream().map(RagService.Source::trecho).toList());
        Set<String> numbers=new HashSet<>(); var found=NUMBER.matcher(evidence); while(found.find())numbers.add(found.group());
        List<String> issues=new ArrayList<>(); var asserted=NUMBER.matcher(text);
        while(asserted.find())if(!numbers.contains(asserted.group()))issues.add("Número sem evidência na página citada: "+asserted.group());
        if(NEGATION.matcher(text).find()&&!NEGATION.matcher(evidence).find())issues.add("Afirmação negativa sem marcador de negação nas fontes citadas");
        return List.copyOf(issues);
    }
}
