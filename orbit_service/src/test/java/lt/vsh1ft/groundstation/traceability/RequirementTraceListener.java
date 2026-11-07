package lt.vsh1ft.groundstation.traceability;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class RequirementTraceListener implements TestExecutionListener {

    static final String REQUIREMENTS_DIRECTORY = "groundstation.requirements.directory";
    static final String REPORT_DIRECTORY = "groundstation.requirements.report";

    private final List<TestOutcome> outcomes = new CopyOnWriteArrayList<>();

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (!test.isTest()) {
            return;
        }
        test.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .ifPresent(source -> trace(source, result.getStatus() == TestExecutionResult.Status.SUCCESSFUL));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        final var requirementsDirectory = System.getProperty(REQUIREMENTS_DIRECTORY);
        final var reportDirectory = System.getProperty(REPORT_DIRECTORY);
        if (requirementsDirectory == null || reportDirectory == null) {
            return;
        }
        final var report = new TraceabilityReport(RequirementSpec.readAll(Path.of(requirementsDirectory)), outcomes);
        write(Path.of(reportDirectory), report);
    }

    private void trace(MethodSource source, boolean passed) {
        final var test = source.getJavaClass().getSimpleName() + "." + source.getMethodName();
        Optional.ofNullable(source.getJavaMethod().getAnnotation(Requirement.class))
                .ifPresent(requirement -> Arrays.stream(requirement.value())
                        .forEach(id -> outcomes.add(new TestOutcome(id, test, passed))));
    }

    private static void write(Path directory, TraceabilityReport report) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("traceability.md"), report.markdown());
            Files.write(directory.resolve("violations.txt"), report.violations());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
