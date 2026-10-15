package br.com.sylph.service;

import java.util.*;
import java.util.regex.Pattern;

/** Detect only identical rule wording with different numbers, without guessing semantic equivalence. */
final class EvidenceConflicts {
    private EvidenceConflicts() { }
    record Conflict(String rule,List<String> quotations) { }
    private static final Pattern QUANTITY=Pattern.compile("(?iu)\\b(?:prazo|limite|dias?|horas?|alunos?)\\b");
    static List<Conflict> detect(List<RagService.Source> sources) {
        Map<String,Map<String,String>> groups=new LinkedHashMap<>();
        for(var source:sources)for(String line:source.trecho().split("\\n|(?<=[.!?])\\s+")) {
            String normalized=line.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").strip();
            if(normalized.length()>220||!QUANTITY.matcher(normalized).find()||!normalized.matches(".*\\d.*"))continue;
            String skeleton=normalized.replaceAll("\\d+(?:[.,]\\d+)?","#");
            groups.computeIfAbsent(skeleton,k->new LinkedHashMap<>()).putIfAbsent(normalized,line+" [Fonte: "+source.documento()+" | Página: "+source.pagina()+"]");
        }
        return groups.entrySet().stream().filter(e->e.getValue().size()>1).map(e->new Conflict(e.getKey(),List.copyOf(e.getValue().values()))).toList();
    }
}
