package lt.vsh1ft.groundstation.traceability;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class TraceabilityReport {

    enum Status { VERIFIED, FAILED, NOT_VERIFIED, OUTSIDE_THE_BUILD }

    private final List<RequirementSpec> requirements;
    private final Map<String, List<TestOutcome>> outcomes;

    TraceabilityReport(List<RequirementSpec> requirements, List<TestOutcome> outcomes) {
        this.requirements = requirements;
        this.outcomes = outcomes.stream().collect(Collectors.groupingBy(TestOutcome::requirement));
    }

    Status status(RequirementSpec requirement) {
        final var traced = outcomes.getOrDefault(requirement.id(), List.of());
        if (traced.isEmpty()) {
            return requirement.verifiedByTest() ? Status.NOT_VERIFIED : Status.OUTSIDE_THE_BUILD;
        }
        return traced.stream().allMatch(TestOutcome::passed) ? Status.VERIFIED : Status.FAILED;
    }

    List<String> violations() {
        return Stream.of(duplicates(), unknownRequirements(), unverifiedTestRequirements())
                .flatMap(Function.identity())
                .toList();
    }

    String markdown() {
        final var verified = requirements.stream().filter(requirement -> status(requirement) == Status.VERIFIED).count();
        final var sections = requirements.stream()
                .collect(Collectors.groupingBy(RequirementSpec::section, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(section -> section(section.getKey(), section.getValue()))
                .collect(Collectors.joining("\n"));
        return """
                # Requirements traceability

                %d requirements, %d verified by passing tests.
                %s""".formatted(requirements.size(), verified, sections);
    }

    private String section(String name, List<RequirementSpec> requirements) {
        return """

                ## %s

                | ID | Requirement | Verification | Status | Tests |
                |---|---|---|---|---|
                %s
                """.formatted(name, requirements.stream().map(this::row).collect(Collectors.joining("\n")));
    }

    private String row(RequirementSpec requirement) {
        final var tests = outcomes.getOrDefault(requirement.id(), List.of()).stream()
                .sorted(Comparator.comparing(TestOutcome::test))
                .map(outcome -> "`" + outcome.test() + "`" + (outcome.passed() ? "" : " (failed)"))
                .collect(Collectors.joining("<br>"));
        return "| %s | %s | %s | %s | %s |".formatted(requirement.id(), requirement.text(), requirement.verification(),
                status(requirement).name().toLowerCase().replace('_', ' '), tests);
    }

    private Stream<String> duplicates() {
        return requirements.stream()
                .collect(Collectors.groupingBy(RequirementSpec::id, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> "Requirement " + entry.getKey() + " is defined " + entry.getValue() + " times")
                .sorted();
    }

    private Stream<String> unknownRequirements() {
        final Set<String> known = requirements.stream().map(RequirementSpec::id).collect(Collectors.toSet());
        return outcomes.values().stream()
                .flatMap(List::stream)
                .filter(outcome -> !known.contains(outcome.requirement()))
                .map(outcome -> outcome.test() + " traces unknown requirement " + outcome.requirement())
                .sorted();
    }

    private Stream<String> unverifiedTestRequirements() {
        return requirements.stream()
                .filter(RequirementSpec::verifiedByTest)
                .filter(requirement -> status(requirement) != Status.VERIFIED)
                .map(requirement -> "Requirement " + requirement.id() + " has no passing test");
    }
}
