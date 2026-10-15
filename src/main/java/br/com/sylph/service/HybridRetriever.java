package br.com.sylph.service;

import br.com.sylph.config.ProjectConfig;
import br.com.sylph.model.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

@Service
public class HybridRetriever {
    public record RetrievalScore(String chunkId, String docId, String documento, List<Integer> paginas,
                                 double cosseno, double bm25, double rrf, double hybridScore, Double rerankScore) { }
    public record RetrievalResult(List<RankedChunk> chunks, List<RankedChunk> candidates,
                                  List<RetrievalScore> scores, String rerankingStatus, String rerankingReason,
                                  long retrievalMillis, long rerankingMillis) { }
    private final OllamaClient ollama;
    private final CsvKnowledgeStore store;
    private final LlmReranker reranker;
    private static final ThreadLocal<RetrievalResult> last = new ThreadLocal<>();
    private final Map<List<KnowledgeChunk>,LexicalIndex> lexicalCache = new IdentityHashMap<>();
    private final Map<List<KnowledgeChunk>,LexicalIndex> legacyLexicalCache = new IdentityHashMap<>();
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern LEGACY_TERMS = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern TERMS = Pattern.compile("[a-z0-9]{2,}|[0-9]");
    public HybridRetriever(OllamaClient ollama, CsvKnowledgeStore store) {
        this.ollama=ollama; this.store=store; this.reranker=new LlmReranker(ollama);
    }
    public static RetrievalResult lastResult() { return last.get(); }
    public static void clearLastResult() { last.remove(); }
    static List<String> terms(String text) { return tokenize(text,false); }
    private static List<String> tokenize(String text,boolean legacy) {
        String plain=DIACRITICS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)).replaceAll("");
        Matcher m=(legacy?LEGACY_TERMS:TERMS).matcher(plain);
        List<String> words=new ArrayList<>(); while(m.find()) words.add(m.group()); return words;
    }
    private static final class LexicalIndex {
        final List<Map<String,Integer>> frequencies=new ArrayList<>();
        final Map<String,Integer> df=new HashMap<>(); final int[] lengths; final double average;
        LexicalIndex(List<KnowledgeChunk> data,boolean legacy) {
            lengths=new int[data.size()];
            for(int i=0;i<data.size();i++) {
                List<String> terms=tokenize(data.get(i).texto(),legacy); lengths[i]=terms.size();
                Map<String,Integer> freq=new HashMap<>(); terms.forEach(t->freq.merge(t,1,Integer::sum));
                frequencies.add(freq); freq.keySet().forEach(t->df.merge(t,1,Integer::sum));
            }
            average=Arrays.stream(lengths).average().orElse(1);
        }
        double[] score(String query,double k1,double b,boolean legacy) {
            return score(new HashSet<>(tokenize(query,legacy)),k1,b);
        }
        double[] score(Set<String> queryTerms,double k1,double b) {
            double[] scores=new double[lengths.length];
            for(int i=0;i<lengths.length;i++) for(String term:queryTerms) {
                int tf=frequencies.get(i).getOrDefault(term,0); if(tf==0)continue;
                int count=df.getOrDefault(term,0); double idf=Math.log(1+(lengths.length-count+.5)/(count+.5));
                scores[i]+=idf*(tf*(k1+1))/(tf+k1*(1-b+b*lengths[i]/Math.max(1,average)));
            }
            return scores;
        }
    }
    static double[] bm25(List<KnowledgeChunk> chunks,String query) { return new LexicalIndex(chunks,true).score(query,1.5,.75,true); }
    static double rrf(int vectorRank,int lexicalRank,int constant) { return 1d/(constant+vectorRank)+1d/(constant+lexicalRank); }
    private synchronized LexicalIndex lexical(List<KnowledgeChunk> chunks,boolean legacy) {
        // Each immutable store snapshot and tokenization variant has its own statistics.
        // Always read/validate the store first; the cache must never bypass index integrity.
        Map<List<KnowledgeChunk>,LexicalIndex> cache=legacy?legacyLexicalCache:lexicalCache;
        LexicalIndex index=cache.get(chunks);
        if(index==null) {
            if(lexicalCache.size()+legacyLexicalCache.size()>=4) { lexicalCache.clear(); legacyLexicalCache.clear(); }
            index=new LexicalIndex(chunks,legacy); cache.put(chunks,index);
        }
        return index;
    }
    public List<RankedChunk> retrieve(ProjectConfig cfg,String question) throws IOException { return retrieveDetailed(cfg,question).chunks(); }
    public RetrievalResult retrieveDetailed(ProjectConfig cfg,String question) throws IOException {
        last.remove();
        if(question==null||question.isBlank()||question.length()>2000)throw new IllegalArgumentException("Pergunta deve conter de 1 a 2000 caracteres.");
        long start=System.nanoTime(); boolean legacy="sp21".equals(cfg.perfil_execucao);
        List<KnowledgeChunk> chunks=legacy?store.readBaseline(cfg.chunking_ativo):store.read(cfg.chunking_ativo);
        boolean vectorEnabled=legacy||!"lexical".equals(cfg.modo_recuperacao), lexicalEnabled=legacy||!"vetorial".equals(cfg.modo_recuperacao);
        for(KnowledgeChunk chunk:chunks) if(!chunk.modeloEmbeddings().isBlank()&&!chunk.modeloEmbeddings().equals(cfg.modelo_embeddings)) throw new IOException("Modelo de embeddings mudou; execute preparar/recriar.");
        List<double[]> queryVectors=vectorEnabled?ollama.embed(cfg,List.of(question)):List.of();
        if(vectorEnabled&&(queryVectors==null||queryVectors.size()!=1||!VectorMath.valid(queryVectors.getFirst())))throw new IOException("Embedding da pergunta vazio ou inválido.");
        double[] q=vectorEnabled?queryVectors.getFirst():null;
        Set<String> queryWords=new HashSet<>(tokenize(question,legacy));
        LexicalIndex index=lexical(chunks,legacy);
        double[] lex=lexicalEnabled?index.score(queryWords,legacy?1.5:cfg.bm25_k1,legacy?.75:cfg.bm25_b):new double[chunks.size()];
        double[] sem=new double[chunks.size()]; double maxCos=-1,maxLex=0;
        for(int i=0;i<chunks.size();i++) {
            if(vectorEnabled) {
                double[] embedding=chunks.get(i).embedding();
                if(!VectorMath.valid(q)||!VectorMath.valid(embedding)||q.length!=embedding.length) throw new IOException("Embedding incompatível com o índice; confira modelo e reindexe.");
                sem[i]=VectorMath.cosine(q,embedding);
            } else sem[i]=-1;
            maxCos=Math.max(maxCos,sem[i]); maxLex=Math.max(maxLex,lex[i]);
        }
        if(legacy&&maxCos<cfg.min_similaridade&&maxLex<2) return finish(List.of(),List.of(),List.of(),"disabled","",start,0);
        List<Integer> ids=IntStream.range(0,chunks.size()).boxed().toList();
        List<Integer> vectors=new ArrayList<>(ids),lexical=new ArrayList<>(ids);
        Comparator<Integer> tie=Comparator.comparing((Integer i)->chunks.get(i).docId()).thenComparing(i->chunks.get(i).id());
        Comparator<Integer> vectorOrder=Comparator.<Integer>comparingDouble(i->sem[i]).reversed();
        Comparator<Integer> lexicalOrder=Comparator.<Integer>comparingDouble(i->lex[i]).reversed();
        vectors.sort(legacy?vectorOrder:vectorOrder.thenComparing(tie));
        lexical.sort(legacy?lexicalOrder:lexicalOrder.thenComparing(tie));
        Map<Integer,Integer> vr=new HashMap<>(),lr=new HashMap<>();
        for(int i=0;i<ids.size();i++) { vr.put(vectors.get(i),i+1); lr.put(lexical.get(i),i+1); }
        Set<Integer> pool=new HashSet<>(); int candidateCount=legacy?12:cfg.candidatos_recuperacao;
        if(vectorEnabled)vectors.stream().filter(i->legacy||sem[i]>=cfg.min_similaridade).limit(candidateCount).forEach(pool::add);
        if(lexicalEnabled)lexical.stream().filter(i->legacy||lex[i]>0).limit(candidateCount).forEach(pool::add);
        List<RankedChunk> all=new ArrayList<>(),candidates=new ArrayList<>(); Map<String,Double> fusion=new HashMap<>();
        for(int i:ids) {
            double f=(vectorEnabled?1d/((legacy?60:cfg.rrf_constante)+vr.get(i)):0)+(lexicalEnabled?1d/((legacy?60:cfg.rrf_constante)+lr.get(i)):0);
            Set<String> words=index.frequencies.get(i).keySet();
            double coverage=queryWords.isEmpty()?0:queryWords.stream().filter(words::contains).count()/(double)queryWords.size();
            double score=(legacy?.65:cfg.peso_vetorial)*Math.max(0,sem[i])+(legacy?.20:cfg.peso_lexical)*(maxLex>0?lex[i]/maxLex:0)+(legacy?.10:cfg.peso_cobertura)*coverage+(legacy?.05:cfg.peso_rrf)*f*30;
            RankedChunk ranked=new RankedChunk(chunks.get(i),sem[i],lex[i],score); all.add(ranked); fusion.put(LlmReranker.key(ranked),f);
            if(pool.contains(i))candidates.add(ranked);
        }
        Comparator<RankedChunk> comparator=Comparator.comparingDouble(RankedChunk::scoreFinal).reversed();
        if(!legacy)comparator=comparator.thenComparing(c->c.chunk().docId()).thenComparing(c->c.chunk().id());
        all.sort(comparator); candidates.sort(comparator);
        // The candidate union is bounded by 2 * candidateCount; the reranker has its own stricter cap.
        LlmReranker.Result reranked=reranker.rerank(cfg,question,candidates);
        List<RankedChunk> primary=reranked.ranked().stream().limit(cfg.top_k).toList();
        List<RankedChunk> expanded=expandNearby(primary,all,cfg.expansao_paginas,legacy?Integer.MAX_VALUE:cfg.trechos_contexto,legacy?16000:cfg.limite_contexto_caracteres);
        List<RetrievalScore> scores=reranked.ranked().stream().map(c->new RetrievalScore(c.chunk().id(),c.chunk().docId(),c.chunk().documento(),c.chunk().spans().stream().map(SourceSpan::pagina).distinct().toList(),c.cosseno(),c.bm25(),fusion.get(LlmReranker.key(c)),c.scoreFinal(),reranked.scores().get(LlmReranker.key(c)))).toList();
        if(cfg.debug_rag)for(var score:scores)System.out.println("[RAG] "+score);
        return finish(expanded,List.copyOf(candidates),scores,reranked.status(),reranked.reason(),start,reranked.milliseconds());
    }
    private RetrievalResult finish(List<RankedChunk> chunks,List<RankedChunk> candidates,List<RetrievalScore> scores,String status,String reason,long start,long rerankMs) {
        RetrievalResult result=new RetrievalResult(chunks,candidates,scores,status,reason,(System.nanoTime()-start)/1_000_000,rerankMs); last.set(result); return result;
    }
    static List<RankedChunk> expandNearby(List<RankedChunk> primary,List<RankedChunk> ranked) { return expandNearby(primary,ranked,2); }
    static List<RankedChunk> expandNearby(List<RankedChunk> primary,List<RankedChunk> ranked,int radius) { return expandNearby(primary,ranked,radius,Integer.MAX_VALUE,16000); }
    private static List<RankedChunk> expandNearby(List<RankedChunk> primary,List<RankedChunk> ranked,int radius,int count,int budget) {
        List<RankedChunk> ordered=new ArrayList<>(primary),result=new ArrayList<>(); Set<String> seen=new HashSet<>(); int characters=0;
        for(int distance=0;distance<=radius;distance++) {
            final int d=distance;
            ranked.stream().filter(c->primary.stream().anyMatch(seed->c.chunk().docId().equals(seed.chunk().docId())&&c.chunk().spans().stream().anyMatch(a->seed.chunk().spans().stream().anyMatch(b->Math.abs(a.pagina()-b.pagina())==d)))).forEach(ordered::add);
        }
        for(RankedChunk c:ordered) { if(!seen.add(LlmReranker.key(c)))continue; int len=c.chunk().texto().length(); if(characters+len>budget||result.size()>=count)continue; result.add(c); characters+=len; }
        return List.copyOf(result);
    }
}
