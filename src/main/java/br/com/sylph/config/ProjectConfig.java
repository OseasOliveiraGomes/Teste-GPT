package br.com.sylph.config;

public class ProjectConfig {
    public String chunking_ativo = "recursivo";
    public String modelo_llm = "sylph-unopar";
    public String modelo_embeddings = "nomic-embed-text";
    public String ollama_url = "http://127.0.0.1:11434";
    public int top_k = 4;
    public boolean debug_rag = false;
    public int expansao_paginas = 2;
    public double min_similaridade = 0.35;
    public double limiar_semantico = 0.66;
    public String perfil_execucao = "sp22";
    public String modo_recuperacao = "hybrid";
    public int chunk_max_chars = 1350;
    public int chunk_min_chars = 280;
    public int chunk_overlap_chars = 200;
    public int semantic_unit_chars = 220;
    public boolean chunk_cross_pages = true;
    public int semantic_embedding_cache_size = 20000;
    public int candidatos_recuperacao = 12;
    public int candidatos_reranking = 12;
    public int trechos_contexto = 24;
    public int limite_contexto_caracteres = 16000;
    public double peso_vetorial = .65;
    public double peso_lexical = .20;
    public double peso_cobertura = .10;
    public double peso_rrf = .05;
    public double bm25_k1 = 1.5;
    public double bm25_b = .75;
    public int rrf_constante = 60;
    public boolean reranking_ativo = false;
    public int reranking_timeout_segundos = 45;
    public int reranking_limite_caracteres = 18000;
    public int ollama_timeout_segundos = 360;
    public int ollama_tentativas = 2;
    public int ollama_keep_alive_segundos = 900;
    public int cache_embeddings_entradas = 2048;
    public long max_documento_bytes = 50L * 1024 * 1024;
    public int max_documento_caracteres = 5_000_000;
    public double temperatura_llm = .1;
    public int seed_llm = 42;
    public boolean llm_think = true;

    public void validar() {
        if (!"recursivo".equals(chunking_ativo) && !"semantico".equals(chunking_ativo)) {
            throw new IllegalArgumentException("chunking_ativo precisa ser recursivo ou semantico");
        }
        if (expansao_paginas < 0 || expansao_paginas > 5) throw new IllegalArgumentException("expansao_paginas entre 0 e 5");
        if (top_k < 1 || top_k > 10) throw new IllegalArgumentException("top_k entre 1 e 10");
        if (min_similaridade < -1 || min_similaridade > 1) throw new IllegalArgumentException("min_similaridade fora do intervalo");
        if (limiar_semantico < -1 || limiar_semantico > 1) throw new IllegalArgumentException("limiar_semantico fora do intervalo");
        if (!Double.isFinite(min_similaridade) || !Double.isFinite(limiar_semantico)) throw new IllegalArgumentException("Limiares precisam ser finitos");
        if (!"sp21".equals(perfil_execucao) && !"sp22".equals(perfil_execucao)) throw new IllegalArgumentException("perfil_execucao: sp21|sp22");
        if (!java.util.Set.of("hybrid", "vetorial", "lexical").contains(modo_recuperacao == null ? "" : modo_recuperacao)) throw new IllegalArgumentException("modo_recuperacao: hybrid|vetorial|lexical");
        if (chunk_max_chars < 100 || chunk_max_chars > 16000 || chunk_min_chars < 1 || chunk_min_chars > chunk_max_chars || chunk_overlap_chars < 0 || chunk_overlap_chars >= chunk_max_chars) throw new IllegalArgumentException("Tamanhos/overlap de chunk inválidos");
        range(semantic_unit_chars, 20, chunk_max_chars, "semantic_unit_chars");
        range(semantic_embedding_cache_size, 0, 100000, "semantic_embedding_cache_size");
        range(candidatos_recuperacao, 1, 1000, "candidatos_recuperacao");
        range(candidatos_reranking, 1, 100, "candidatos_reranking");
        range(trechos_contexto, 1, 1000, "trechos_contexto");
        range(limite_contexto_caracteres, 100, 100000, "limite_contexto_caracteres");
        range(reranking_limite_caracteres, 100, 100000, "reranking_limite_caracteres");
        range(rrf_constante, 1, 10000, "rrf_constante");
        range(reranking_timeout_segundos, 1, 600, "reranking_timeout_segundos");
        range(ollama_timeout_segundos, 1, 3600, "ollama_timeout_segundos");
        range(ollama_tentativas, 1, 4, "ollama_tentativas");
        range(ollama_keep_alive_segundos, 0, 86400, "ollama_keep_alive_segundos");
        range(cache_embeddings_entradas, 0, 100000, "cache_embeddings_entradas");
        for (double weight : new double[]{peso_vetorial, peso_lexical, peso_cobertura, peso_rrf}) if (!Double.isFinite(weight) || weight < 0 || weight > 1) throw new IllegalArgumentException("Pesos entre 0 e 1");
        if (peso_vetorial + peso_lexical + peso_cobertura + peso_rrf <= 0) throw new IllegalArgumentException("Ao menos um peso positivo");
        if (!Double.isFinite(bm25_k1) || bm25_k1 <= 0 || bm25_k1 > 10 || !Double.isFinite(bm25_b) || bm25_b < 0 || bm25_b > 1) throw new IllegalArgumentException("Parâmetros BM25 inválidos");
        if (max_documento_bytes < 1 || max_documento_bytes > 1024L*1024*1024 || max_documento_caracteres < 1 || max_documento_caracteres > 50_000_000) throw new IllegalArgumentException("Limites de documento inválidos");
        if (modelo_llm == null || modelo_llm.isBlank() || modelo_embeddings == null || modelo_embeddings.isBlank()) throw new IllegalArgumentException("Informe os modelos Ollama");
        if (!Double.isFinite(temperatura_llm) || temperatura_llm < 0 || temperatura_llm > 2) throw new IllegalArgumentException("temperatura_llm entre 0 e 2");
        try {
            var uri = java.net.URI.create(ollama_url);
            String host = uri.getHost();
            if (!"http".equals(uri.getScheme()) || !java.util.Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host == null ? "" : host.toLowerCase(java.util.Locale.ROOT)) || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new IllegalArgumentException("ollama_url deve ser HTTP local, sem credenciais ou caminho", ex); }
    }

    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " entre " + min + " e " + max);
    }
}
