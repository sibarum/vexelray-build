package dev.vexelray.build;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code isolate}: every platform-specific line of a component lives in that component's {@code -windows},
 * {@code -linux} or {@code -macos} module, and nowhere else.
 *
 * <p>The rule the stack is built on: shared code never asks which OS it is on and never names an OS's library, shell
 * or device; it asks an interface, and an OS module answers, found through {@code ServiceLoader} and put on the class
 * path by an OS-activated profile. Then a new platform is new modules, one beside each {@code -windows} one, and
 * nothing to hunt for in the code that is already there. This goal is what keeps that true: in any other module it
 * fails the build on a line of {@code src/main/java} that bears one of {@link PlatformMarks#MARKS} (comments do not
 * count), and names the file and line.
 *
 * <p>In a {@code -windows} module it also says when there is no {@code -macos} module beside it yet: the list of
 * what a macOS port has still to write, kept by the build rather than by memory.
 *
 * <p>A module that is one OS's by nature under a name that does not say so (a bridge to DXGI, say) declares it with
 * {@code <os>windows</os>} in this goal's configuration, rather than renaming an artifact others depend on.
 *
 * <p>{@code -Dvexelray.isolate.fail=false} reports without failing; {@code -Dvexelray.isolate.skip=true} skips.
 */
@Mojo(name = "isolate", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class IsolateMojo extends AbstractMojo {

    /** A module whose artifactId ends in one of these is an OS module, where platform-specific code belongs. */
    static final Pattern OS_MODULE = Pattern.compile(".*-(windows|linux|macos)$");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Fail the build on a mark outside an OS module, rather than only reporting it. */
    @Parameter(property = "vexelray.isolate.fail", defaultValue = "true")
    private boolean fail;

    @Parameter(property = "vexelray.isolate.skip", defaultValue = "false")
    private boolean skip;

    /** The OS this module is for when its artifactId does not say: {@code windows}, {@code linux} or {@code macos}. */
    @Parameter
    private String os;

    @Override
    public void execute() throws MojoFailureException {
        if (skip || "pom".equals(project.getPackaging())) {
            return;
        }
        String artifact = project.getArtifactId();
        Path base = project.getBasedir().toPath();
        if (os != null && !os.isBlank()) {
            if (!os.matches("windows|linux|macos")) {
                throw new MojoFailureException(artifact + " declares <os>" + os + "</os>: windows, linux or macos");
            }
            return;
        }
        if (OS_MODULE.matcher(artifact).matches()) {
            reportMissingMacos(artifact, base);
            return;
        }
        List<String> found = new ArrayList<>();
        for (String root : project.getCompileSourceRoots()) {
            Path dir = Path.of(root);
            if (!Files.isDirectory(dir) || !dir.startsWith(base.resolve("src"))) {
                continue;   // generated sources are the generator's business
            }
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    String source = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    for (PlatformMarks.Found f : PlatformMarks.scan(source)) {
                        found.add(base.relativize(file).toString().replace('\\', '/') + ":" + f.line() + ": "
                                + f.what() + ": " + f.text());
                    }
                }
            } catch (IOException e) {
                throw new MojoFailureException("could not read the sources of " + artifact + ": " + e.getMessage(), e);
            }
        }
        if (found.isEmpty()) {
            return;
        }
        String head = artifact + " has " + found.size() + " platform-specific line" + (found.size() == 1 ? "" : "s")
                + " outside an OS module. Move each behind an interface, answered by this component's -windows, "
                + "-linux (and later -macos) modules:";
        if (fail) {
            found.forEach(f -> getLog().error("  " + f));
            throw new MojoFailureException(head + "\n  " + String.join("\n  ", found));
        }
        getLog().warn(head);
        found.forEach(f -> getLog().warn("  " + f));
    }

    /** From a {@code -windows} module: say so when its {@code -macos} sibling does not exist yet. */
    private void reportMissingMacos(String artifact, Path base) {
        if (!artifact.endsWith("-windows")) {
            return;
        }
        String macos = artifact.substring(0, artifact.length() - "-windows".length()) + "-macos";
        Path parent = base.getParent();
        if (parent != null && !Files.isDirectory(parent.resolve(macos))) {
            getLog().info("macOS: " + macos + " is still to write (beside " + artifact + ")");
        }
    }
}
