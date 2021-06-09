//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS commons-io:commons-io:2.6
//JAVA 16

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

public class ComparaArquivosTransferencia {

    private static final String SEPARADOR = ";";
    public static final String CL_EST = "UF";
    public static final String CL_MUN = "NOME MUNICÍPIO";
    public static final String CL_VALOR = "VALOR TRANSFERIDO";

    record Linha(String municipio, double valor) {
    }

    public static void main(String args[]) throws IOException {

        if (args.length < 2) {
            System.out.println("São necessários dois parâmetros com dois caminhos para diretorio base de transferencia");
            System.exit(1);
        }

        var backupPath = args[0];
        var novoPath = args[1];

        Files.walk(Paths.get(backupPath), 2)
             .filter(p -> p.toString().endsWith("zip"))
             .forEach(p -> {
                 var novo = Paths.get(novoPath, p.toFile().getName());

                 if (!novo.toFile().exists()) {
                     System.out.println("Arquivo somente encontrado no backup:");
                     System.out.println(p.toFile().getName());
                     return;
                 }

                 try {
                     processa(p, novo);
                 } catch (IOException e) {
                     System.out.printf("Erro processando %s / %s", p, novo);
                     e.printStackTrace();
                 }

             });

    }

    public static void processa(Path p1, Path p2) throws IOException {
        var relatorio = new StringBuffer();
        var csv = new StringBuffer();
        var erros = new StringBuffer();

        System.out.printf("INICIANDO ANALISE ARQUIVOS: %s - %s\n", p1, p2);

        relatorio.append("## Relatório de transferências do governo federal para municípios\n");

        var linhasP1 = linhas(p1, erros);
        var linhasP2 = linhas(p2, erros);

        relatorio.append("### Arquivos analisados:\n");
        final String templateContagem = "* %s (%d linhas)\n";
        relatorio.append(templateContagem.formatted(p1, linhasP1.size()));
        relatorio.append(templateContagem.formatted(p2, linhasP2.size()));

        relatorio.append("### Total Transferências:\n");
        final String templateSoma = "* %s: %f\n";
        relatorio.append(templateSoma.formatted(p1, soma(linhasP1)));
        relatorio.append(templateSoma.formatted(p2, soma(linhasP2)));

        var contagemP1 = contaPorCidade(linhasP1);
        var contagemP2 = contaPorCidade(linhasP2);

        var somaP1 = somaPorCidade(linhasP1);
        var somaP2 = somaPorCidade(linhasP2);

        final var cabecalhoTabela =
                """
                        | Mun | %s | %s | Diff | Percent |
                        | --- | --- | --- | --- | --- |
                        """
                           .formatted(p1, p2);

        final var cabecalhoCSV = """
                "Mun","%s","%s","Diff","Percent"
                """
                   .formatted(p1, p2);
        relatorio.append("### Diferença de valores totais de transferências por município:\n");
        relatorio.append(cabecalhoTabela);
        csv.append(cabecalhoCSV);
        var mudancasValor = new AtomicBoolean(false);
        var mudancasLinha = new AtomicBoolean(false);
        somaP1.forEach((m, l) -> {
            var l2 = somaP2.get(m);
            if (l2 != null && Math.abs(l - l2) >= 0.0001) {
                var diff = l2 - l;
                var percent = (diff * 100.0) / l;
                relatorio.append("| %s | %.2f | %.2f | %.2f | %.2f |\n".formatted(m, l, l2, diff, percent));
                csv.append("\"%s\",%.2f,%.2f,%.2f,%.2f\n".formatted(m, l, l2, diff, percent));
                mudancasValor.set(true);
            }
        });

        relatorio.append("### Diferença de número de transferência por município:\n");
        relatorio.append(cabecalhoTabela);
        contagemP1.forEach((m, l) -> {
            var l2 = contagemP2.get(m);

            if (l2 != null && l.size() != l2.size()) {
                var diff = l2.size() - l.size();
                var percent = (diff * 100) / l.size();
                relatorio.append("| %s | %s | %s | %d | %d |\n".formatted(m, l.size(), l2.size(), diff, percent));
                mudancasLinha.set(true);
            }
        });

        var nomeBase = p1.toFile().getName() + "_" + p2.toFile().getName();
        var nomeSaidaRelatorio = "relatorios/" + nomeBase + ".md";
        var nomeSaidaCsv = "csvs/" + nomeBase + ".csv";
        var nomeSaidaErro = "erros/" + nomeBase + ".txt";

        if (mudancasLinha.get() || mudancasValor.get()) {
            System.out.println(">>> Diferenças identificadas - salvando dados");
            Files.writeString(Paths.get(nomeSaidaRelatorio), relatorio.toString());
            Files.writeString(Paths.get(nomeSaidaCsv), csv.toString());
            Files.writeString(Paths.get(nomeSaidaErro), erros.toString());
        } else {
            System.out.println(">>> Não há diferença!");
        }
        System.out.printf("Análise terminada para %s e  %s\n", p1, p2);

    }

    private static Double soma(List<ComparaArquivosTransferencia.Linha> linhas) {
        return linhas.stream().collect(Collectors.summingDouble(Linha::valor));
    }

    private static Map<String, Double> somaPorCidade(List<ComparaArquivosTransferencia.Linha> linhas) {
        return linhas.stream().collect(Collectors.groupingBy(Linha::municipio, Collectors.summingDouble(Linha::valor)));
    }

    private static Map<String, List<ComparaArquivosTransferencia.Linha>> contaPorCidade(List<ComparaArquivosTransferencia.Linha> linhas) {
        return linhas.stream().collect(Collectors.groupingBy(l -> l.municipio()));
    }

    public static List<Linha> linhas(Path p, StringBuffer saidaErro) throws IOException {
        var i = new AtomicInteger();
        var linhas = new ArrayList<Linha>();

        var munIdx = new AtomicInteger(-1);
        var estIdx = new AtomicInteger(-1);
        var valorIdx = new AtomicInteger(-1);

        var temp = pegaCSV(p);
        Files.lines(temp, StandardCharsets.UTF_8)
             .forEach(l -> {
                 var colunas = l.split(SEPARADOR);
                 if (i.getAndIncrement() == 0) {
                     try {
                         buscaColuna(valorIdx, CL_VALOR, colunas);
                         buscaColuna(estIdx, CL_EST, colunas);
                         buscaColuna(munIdx, CL_MUN, colunas);
                     } catch (Exception e) {
                         throw e;
                     }

                 } else {
                     try {
                         var valor = limpa(colunas[valorIdx.get()])
                                                                   .replaceAll("\\,", ".");
                         var mun = limpa(colunas[munIdx.get()]);
                         var est = limpa(colunas[estIdx.get()]);

                         if (mun == null || mun.isBlank()) {
                             mun = "ESTADO";
                         }

                         if (est == null || est.isBlank()) {
                             est = "SEM ESTADO";
                         }

                         double parseDouble = Double.parseDouble(valor);
                         var linha = new Linha(mun + " - " + est,
                                               parseDouble);
                         linhas.add(linha);
                     } catch (Exception e) {
                         System.out.println("ERROR NA LINHA " + i.get());
                         saidaErro.append("Erro na linha %d do arquivo %s \n".formatted(i.get(), p));
                         return;
                     }

                 }
             });
        try {
            Files.delete(temp);
        } catch (IOException e) {
            System.out.println("Erro removendo arquivo CSV temporario - apague");
        }
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

    public static Path pegaCSV(Path p) throws IOException {
        try (ZipFile arquivoZip = new ZipFile(p.toFile())) {
            ZipEntry entryCSV = arquivoZip.entries().nextElement();
            String dadosData = entryCSV.toString().substring(0, 6);
            Path arquivoCSV = Files.createTempFile(dadosData, ".csv");

            IOUtils.copy(arquivoZip.getInputStream(entryCSV), new FileWriter(
                                                                             arquivoCSV.toFile()), StandardCharsets.ISO_8859_1);
            return arquivoCSV;
        }
    }
}
