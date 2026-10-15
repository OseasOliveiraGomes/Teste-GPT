param(
    [string]$Jar = 'target/sylph-unopar-java-1.0.0.jar',
    [switch]$ValidarSomente,
    [string]$Perguntas = 'testes/perguntas_academicas.json'
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jarPath = (Resolve-Path -LiteralPath $Jar).Path
# Validation reads only JAR entries: no extraction, compilation, Ollama calls or index writes.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    foreach ($requiredEntry in @(
        'BOOT-INF/classes/br/com/sylph/SylphApplication.class',
        'BOOT-INF/classes/br/com/sylph/service/DevTerminal.class',
        'BOOT-INF/classes/br/com/sylph/service/AcademicEvaluation.class',
        'BOOT-INF/classes/br/com/sylph/service/HybridRetriever.class'
    )) {
        if ($null -eq $archive.GetEntry($requiredEntry)) {
            throw "JAR sem a classe obrigatória '$requiredEntry'. Execute mvn clean package."
        }
    }
} finally { $archive.Dispose() }
if ($ValidarSomente) {
    Write-Host 'JAR validado pela presença das classes. Nenhuma chamada ao Ollama ou resultado acadêmico foi gerado.'
    return
}
$questionsPath = (Resolve-Path -LiteralPath $Perguntas).Path
Write-Host 'Executando a referência SP 2.1: recursivo e semântico, sem reranking; configuração original preservada.'
& java '-Dfile.encoding=UTF-8' -jar $jarPath 'avaliar-sp21' $questionsPath
if ($LASTEXITCODE -ne 0) { throw 'Experimento interrompido. Consulte os registros da avaliação em resultados/.' }
