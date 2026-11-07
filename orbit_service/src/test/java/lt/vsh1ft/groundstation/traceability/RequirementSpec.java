package lt.vsh1ft.groundstation.traceability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

record RequirementSpec(String id, String text, String verification, String section) {

    static final String TEST = "T";

    private static final String EXTENSION = ".yml";

    boolean verifiedByTest() {
        return TEST.equals(verification);
    }

    static List<RequirementSpec> readAll(Path directory) {
        try (var files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(EXTENSION))
                    .sorted()
                    .flatMap(file -> read(file).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<RequirementSpec> read(Path file) {
        final var name = file.getFileName().toString();
        final var section = name.substring(0, name.length() - EXTENSION.length());
        try (var reader = Files.newBufferedReader(file)) {
            final List<Map<String, String>> entries = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            return entries.stream()
                    .map(entry -> new RequirementSpec(entry.get("id"), entry.get("text"), entry.get("verification"),
                            section))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
