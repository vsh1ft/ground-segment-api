package lt.vsh1ft.groundstation.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceabilityReportTest {

    private static final RequirementSpec REFRESH = new RequirementSpec("CAT-050", "Refresh the catalog", "T", "catalog");
    private static final RequirementSpec SEARCH = new RequirementSpec("CAT-030", "Search the catalog", "T", "catalog");
    private static final RequirementSpec OPENAPI = new RequirementSpec("API-020", "Describe the API", "I", "api");

    @Test
    void requirementWithOnlyPassingTestsIsVerified() {
        final var report = new TraceabilityReport(List.of(REFRESH), List.of(
                new TestOutcome("CAT-050", "CatalogRefreshTest.groups", true),
                new TestOutcome("CAT-050", "CatalogRefreshTest.schedule", true)));

        assertThat(report.status(REFRESH)).isEqualTo(TraceabilityReport.Status.VERIFIED);
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void requirementWithAFailingTestIsNotVerified() {
        final var report = new TraceabilityReport(List.of(REFRESH), List.of(
                new TestOutcome("CAT-050", "CatalogRefreshTest.groups", true),
                new TestOutcome("CAT-050", "CatalogRefreshTest.schedule", false)));

        assertThat(report.status(REFRESH)).isEqualTo(TraceabilityReport.Status.FAILED);
        assertThat(report.violations()).containsExactly("Requirement CAT-050 has no passing test");
    }

    @Test
    void testRequirementWithoutTestsIsAViolation() {
        final var report = new TraceabilityReport(List.of(REFRESH, SEARCH), List.of(
                new TestOutcome("CAT-050", "CatalogRefreshTest.groups", true)));

        assertThat(report.status(SEARCH)).isEqualTo(TraceabilityReport.Status.NOT_VERIFIED);
        assertThat(report.violations()).containsExactly("Requirement CAT-030 has no passing test");
    }

    @Test
    void requirementVerifiedByInspectionNeedsNoTest() {
        final var report = new TraceabilityReport(List.of(OPENAPI), List.of());

        assertThat(report.status(OPENAPI)).isEqualTo(TraceabilityReport.Status.OUTSIDE_THE_BUILD);
        assertThat(report.violations()).isEmpty();
    }

    @Test
    void testTracingAnUnknownRequirementIsAViolation() {
        final var report = new TraceabilityReport(List.of(REFRESH), List.of(
                new TestOutcome("CAT-050", "CatalogRefreshTest.groups", true),
                new TestOutcome("CAT-055", "CatalogRefreshTest.typo", true)));

        assertThat(report.violations()).containsExactly("CatalogRefreshTest.typo traces unknown requirement CAT-055");
    }

    @Test
    void requirementDefinedTwiceIsAViolation() {
        final var report = new TraceabilityReport(List.of(OPENAPI, OPENAPI), List.of());

        assertThat(report.violations()).containsExactly("Requirement API-020 is defined 2 times");
    }

    @Test
    void markdownGroupsRequirementsBySectionWithTheirStatusAndTests() {
        final var report = new TraceabilityReport(List.of(REFRESH, OPENAPI), List.of(
                new TestOutcome("CAT-050", "CatalogRefreshTest.groups", true),
                new TestOutcome("CAT-050", "CatalogRefreshTest.schedule", false)));

        assertThat(report.markdown())
                .contains("2 requirements, 0 verified by passing tests.")
                .containsSubsequence("## catalog", "| CAT-050 |", "## api", "| API-020 |")
                .contains("| CAT-050 | Refresh the catalog | T | failed | "
                        + "`CatalogRefreshTest.groups`<br>`CatalogRefreshTest.schedule` (failed) |")
                .contains("| API-020 | Describe the API | I | outside the build |  |");
    }

    @Test
    void requirementsAreReadFromEveryYamlFileInTheFolderWithTheFileAsSection(@TempDir Path directory)
            throws IOException {
        Files.writeString(directory.resolve("catalog.yml"), """
                - id: CAT-050
                  text: >-
                    Refresh the catalog
                    every 4 hours.
                  verification: T
                """);
        Files.writeString(directory.resolve("api.yml"), """
                - id: API-020
                  text: Describe the API
                  verification: I
                """);
        Files.writeString(directory.resolve("notes.txt"), "not a requirements file");

        assertThat(RequirementSpec.readAll(directory)).containsExactly(
                new RequirementSpec("API-020", "Describe the API", "I", "api"),
                new RequirementSpec("CAT-050", "Refresh the catalog every 4 hours.", "T", "catalog"));
    }
}
