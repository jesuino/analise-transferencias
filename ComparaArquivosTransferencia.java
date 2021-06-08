//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS commons-io:commons-io:2.6
//JAVA 16

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ComparaArquivosTransferencia {

    private static final String SEPARADOR = ";";
    public static final String CL_EST = "UF";
    public static final String CL_MUN = "NOME MUNICÍPIO";
    public static final String CL_VALOR = "VALOR TRANSFERIDO";

    record Linha(String municipio, double valor) {
    }

    public static void main(String args[]) throws IOException {
        if (args.length < 2) {
            System.out.println("São necessários dois parâmetros com dois caminhos para arquivos de transferência");
            System.exit(1);
        }
        var relatorio = new StringBuffer();
        var arquivo1 = args[0];
        var arquivo2 = args[1];
        System.out.println("INICIANDO ANALISE ARQUIVOS");
        var p1 = Paths.get(arquivo1);
        var p2 = Paths.get(arquivo2);

        relatorio.append("## Relatório de transferências do governo federal para municípios\n");

        var linhasP1 = linhas(p1);
        var linhasP2 = linhas(p2);

        final String templateContagem = "* %s (%d linhas)\n";
        relatorio.append("### Arquivos analisados:\n");
        relatorio.append(templateContagem.formatted(p1, linhasP1.size()));
        relatorio.append(templateContagem.formatted(p2, linhasP2.size()));

        // CONTAR LINHAS POR CIDADE E SOMAR

        // SOMAR LINHAS POR CIDADE E COMPARAR

        var resumoP1 = contaPorCidade(linhasP1);
        var resumoP2 = contaPorCidade(linhasP2);

        relatorio.append("### Diferença de número de transferência por município:\n");

        final String cabecalhoTabela =
                """
                        | Mun | %s | %s |
                        | --- | --- | --- |
                        """
                   .formatted(p1.toFile().getName(), p2.toFile().getName());
        relatorio.append(cabecalhoTabela);
        resumoP1.forEach((m, l) -> {
            var l2 = resumoP2.get(m);

            if (l2 != null && l.size() != l2.size()) {
                relatorio.append("| %s | %s | %s |\n".formatted(m, l.size(), l2.size()));
            }
        });
        
        var nomeSaida = p1.toFile().getName() + "_" + p2.toFile().getName() + ".md";
        
        Files.writeString(Paths.get(nomeSaida), relatorio.toString());
        System.out.println("Análise terminada");

    }

    private static Map<String, List<ComparaArquivosTransferencia.Linha>> contaPorCidade(List<ComparaArquivosTransferencia.Linha> linhas) {
        return linhas.stream().collect(Collectors.groupingBy(l -> l.municipio()));
    }

    public static List<Linha> linhas(Path p) throws IOException {
        var i = new AtomicInteger();
        var linhas = new ArrayList<Linha>();

        var munIdx = new AtomicInteger(-1);
        var estIdx = new AtomicInteger(-1);
        var valorIdx = new AtomicInteger(-1);

        Files.lines(p, StandardCharsets.ISO_8859_1)
             .forEach(l -> {
                 var colunas = l.split(SEPARADOR);
                 if (i.getAndIncrement() == 0) {
                     buscaColuna(valorIdx, CL_VALOR, colunas);
                     buscaColuna(estIdx, CL_EST, colunas);
                     buscaColuna(munIdx, CL_MUN, colunas);

                 } else {
                     var valor = limpa(colunas[valorIdx.get()])
                                                               .replaceAll("\\,", ".");
                     var mun = limpa(colunas[munIdx.get()]);
                     var est = limpa(colunas[estIdx.get()]);
                     var linha = new Linha(mun + " - " + est,
                                           Double.parseDouble(valor));
                     linhas.add(linha);
                 }
             });
        return linhas;

    }

    private static String limpa(String v) {
        return v.replaceAll("\"", "");
    }

    private static void buscaColuna(AtomicInteger idx, String coluna, String[] colunas) {
        for (int j = 0; j < colunas.length; j++) {
            if (coluna.equals(limpa(colunas[j]))) {
                idx.set(j);
                return;
            }

        }
        throw new IllegalArgumentException("Coluna " + coluna + " não encontrada");
    }
}
