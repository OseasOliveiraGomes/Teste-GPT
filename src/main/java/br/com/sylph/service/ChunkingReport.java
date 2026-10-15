package br.com.sylph.service;

import br.com.sylph.model.SourceSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Measurements from exact source intervals; no target chunk counts are imposed. */
public final class ChunkingReport {
    private ChunkingReport() { }
    public record Statistics(int chunks,int minimum,int maximum,double average,Map<String,Long> distribution,
                             long pages,long sections,double originalCoveragePercent,double rawCoveragePercent,
                             double repeatedSourcePercent,List<String> potentialDuplicates,long processingMillis) { }
    public record Comparison(Statistics recursive,Statistics semantic,double identicalSegmentsPercent,
                             double textualOverlapPercent,String overlapDefinition,String explanation) { }
    public static Comparison compare(List<PdfPages.Page> pages,List<TextChunker.Segment> recursive,
                                     List<TextChunker.Segment> semantic,long recursiveMs,long semanticMs) {
        Statistics a=stats(pages,recursive,recursiveMs),b=stats(pages,semantic,semanticMs);
        Set<String> rt=new HashSet<>(),st=new HashSet<>(); recursive.forEach(s->rt.add(normal(s.text()))); semantic.forEach(s->st.add(normal(s.text())));
        long identical=recursive.stream().filter(s->st.contains(normal(s.text()))).count()+semantic.stream().filter(s->rt.contains(normal(s.text()))).count();
        double same=recursive.size()+semantic.size()==0?0:100d*identical/(recursive.size()+semantic.size());
        double overlap=(bestOverlap(recursive,semantic)+bestOverlap(semantic,recursive))/2;
        String explanation=same>80?"Segmentações semelhantes: unidades curtas/relacionadas cabem nos limites e similaridades não justificam transições adicionais. O algoritmo não força diferenças para alterar métricas.":"Diferenças refletem limites estruturais e transições entre embeddings de unidades adjacentes; a divisão por página não é obrigatória. A inspeção humana dos limites continua necessária.";
        return new Comparison(a,b,same,overlap,"Média simétrica do melhor Jaccard de conjuntos de palavras por segmento (0–100); diferente da cobertura da fonte e da repetição por overlap.",explanation);
    }
    public static Statistics stats(List<PdfPages.Page> pages,List<TextChunker.Segment> chunks,long milliseconds) {
        IntSummaryStatistics sizes=chunks.stream().mapToInt(s->s.text().length()).summaryStatistics();
        Map<String,Long> distribution=new LinkedHashMap<>(); for(String key:List.of("1-279","280-699","700-1349","1350+"))distribution.put(key,0L);
        Map<Integer,int[]> covered=new HashMap<>(); pages.forEach(p->covered.put(p.number(),new int[p.text().length()]));
        Map<String,Integer> texts=new LinkedHashMap<>(); Set<Integer> seenPages=new HashSet<>();
        for(var chunk:chunks) {
            int len=chunk.text().length(); String bin=len<280?"1-279":len<700?"280-699":len<1350?"700-1349":"1350+";
            distribution.merge(bin,1L,Long::sum); texts.merge(normal(chunk.text()),1,Integer::sum);
            for(SourceSpan span:chunk.spans()) {
                int[] coverage=covered.get(span.pagina()); if(coverage==null)throw new IllegalArgumentException("Página de origem inexistente");
                if(span.inicio()<0||span.fim()>coverage.length)throw new IllegalArgumentException("Intervalo fora da página");
                String original=pages.stream().filter(p->p.number()==span.pagina()).findFirst().orElseThrow().text();
                if(!original.substring(span.inicio(),span.fim()).equals(span.texto()))throw new IllegalArgumentException("Texto do intervalo não coincide com a origem");
                seenPages.add(span.pagina()); for(int i=span.inicio();i<span.fim();i++)coverage[i]++;
            }
        }
        long total=0,nonwhite=0,rawCovered=0,textCovered=0,repeated=0,occurrences=0,sections=0;
        for(var page:pages) {
            int[] coverage=covered.get(page.number()); total+=coverage.length;
            for(int i=0;i<coverage.length;i++) { if(coverage[i]>0)rawCovered++; if(!Character.isWhitespace(page.text().charAt(i))) { nonwhite++; if(coverage[i]>0)textCovered++; } if(coverage[i]>1)repeated+=coverage[i]-1; occurrences+=coverage[i]; }
            int offset=0; for(String line:page.text().split("\n",-1)) { if(TextChunker.isHeading(line)) { boolean present=false; for(int i=offset;i<Math.min(coverage.length,offset+line.length());i++)present|=coverage[i]>0; if(present)sections++; } offset+=line.length()+1; }
        }
        List<String> duplicates=new ArrayList<>(); texts.forEach((text,count)->{if(count>1)duplicates.add(count+" ocorrências: "+text.substring(0,Math.min(100,text.length())));});
        if(repeated>0)duplicates.add("Caracteres de origem repetidos (inclui overlap intencional): "+repeated);
        return new Statistics(chunks.size(),chunks.isEmpty()?0:sizes.getMin(),chunks.isEmpty()?0:sizes.getMax(),sizes.getAverage(),distribution,seenPages.size(),sections,nonwhite==0?100:100d*textCovered/nonwhite,total==0?100:100d*rawCovered/total,occurrences==0?0:100d*repeated/occurrences,List.copyOf(duplicates),milliseconds);
    }
    private static double bestOverlap(List<TextChunker.Segment> from,List<TextChunker.Segment> to) {
        if(from.isEmpty()||to.isEmpty())return 0;
        List<Set<String>> targets=to.stream().map(s->new HashSet<>(Arrays.asList(normal(s.text()).split(" ")))).map(s->(Set<String>)s).toList();
        double total=0;
        for(var s:from) { Set<String> terms=new HashSet<>(Arrays.asList(normal(s.text()).split(" "))); double best=0;
            for(var target:targets) { Set<String> intersection=new HashSet<>(terms); intersection.retainAll(target); int union=terms.size()+target.size()-intersection.size(); if(union>0)best=Math.max(best,intersection.size()/(double)union); } total+=best;
        }
        return total*100/from.size();
    }
    private static String normal(String text) { return text.replaceAll("\\s+"," ").trim(); }
    public static void export(Path directory,String name,Comparison comparison) throws IOException {
        Files.createDirectories(directory); ObjectMapper mapper=new ObjectMapper();
        AcademicEvaluation.atomic(directory.resolve(name+".json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(comparison));
        String header="\uFEFFmetodo,chunks,minimo,maximo,media,paginas,secoes,cobertura_texto_percent,cobertura_bruta_percent,repeticao_fonte_percent,tempo_ms\r\n";
        StringBuilder csv=new StringBuilder(header),md=new StringBuilder("# Validação do chunking\n\n| Método | Chunks | Mín. | Máx. | Média | Páginas | Seções | Cobertura textual | Tempo (ms) |\n|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for(String method:List.of("recursivo","semantico")) { Statistics s=method.equals("recursivo")?comparison.recursive():comparison.semantic();
            csv.append(String.join(",",method,""+s.chunks(),""+s.minimum(),""+s.maximum(),""+s.average(),""+s.pages(),""+s.sections(),""+s.originalCoveragePercent(),""+s.rawCoveragePercent(),""+s.repeatedSourcePercent(),""+s.processingMillis())).append("\r\n");
            md.append("| ").append(method).append(" | ").append(s.chunks()).append(" | ").append(s.minimum()).append(" | ").append(s.maximum()).append(" | ").append(s.average()).append(" | ").append(s.pages()).append(" | ").append(s.sections()).append(" | ").append(s.originalCoveragePercent()).append("% | ").append(s.processingMillis()).append(" |\n");
        }
        for(String method:List.of("recursivo","semantico")) { Statistics s=method.equals("recursivo")?comparison.recursive():comparison.semantic(); md.append("\nDistribuição ").append(method).append(": ").append(s.distribution()).append(". Repetição da fonte: ").append(s.repeatedSourcePercent()).append("%. Possíveis duplicações: ").append(s.potentialDuplicates()).append("\n\n"); }
        md.append("\nSegmentos idênticos: ").append(comparison.identicalSegmentsPercent()).append("%. Sobreposição textual: ").append(comparison.textualOverlapPercent()).append("%.\n\n").append(comparison.overlapDefinition()).append("\n\n").append(comparison.explanation()).append("\n\nCobertura textual considera caracteres não brancos do texto limpo por PDFBox; a cobertura bruta também conta espaços. Seções são títulos detectados por heurística, não anotações humanas. Tempos não incluem embeddings finais dos chunks.\n");
        AcademicEvaluation.atomic(directory.resolve(name+".csv"),csv.toString()); AcademicEvaluation.atomic(directory.resolve(name+".md"),md.toString());
    }
}
