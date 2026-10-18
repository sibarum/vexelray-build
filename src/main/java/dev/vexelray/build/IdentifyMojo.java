package dev.vexelray.build;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code identify}: stamp this module with what it is built from, and write the bill of everything it is made of.
 *
 * <ul>
 *   <li><b>The stamp</b>, {@code META-INF/vexelray/build/<group>/<artifact>.properties} in the module's output: its
 *       coordinates, commit, and the digest of any uncommitted change ({@link Source}). It goes into the jar, so
 *       whatever is built on this module later finds it.</li>
 *   <li><b>The bill</b>, {@code META-INF/vexelray/build.properties} ({@link Bill}): the stamp, plus the stamp of every
 *       part on the runtime class path that has one. An application's code generator reads it while compiling.</li>
 *   <li><b>Properties</b> for the rest of the build: {@code vexelray.build.source} and {@code vexelray.build.commit}.</li>
 * </ul>
 *
 * <p>Runs at {@code initialize}, before anything compiles. A module whose packaging is {@code pom} has no output and
 * gets neither file. Outside a git checkout the commit is {@code unknown}, which says so rather than failing the build;
 * {@code -Dvexelray.build.strict=true} makes it fail instead.
 */
@Mojo(name = "identify", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class IdentifyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Fail when the source cannot be known (no git, or not a checkout), rather than stamping {@code unknown}. */
    @Parameter(property = "vexelray.build.strict", defaultValue = "false")
    private boolean strict;

    /** Skip this module. */
    @Parameter(property = "vexelray.build.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip || "pom".equals(project.getPackaging())) {
            return;
        }
        try {
            String[] source = Source.of(project.getBasedir().toPath());
            if (strict && Source.UNKNOWN.equals(source[0])) {
                throw new MojoExecutionException("the source of " + project.getArtifactId() + " cannot be known: "
                        + project.getBasedir() + " is not in a git checkout, or git is not on the PATH");
            }
            Stamp self = new Stamp(project.getGroupId(), project.getArtifactId(), project.getVersion(), source[0],
                    source[1]);
            Path out = Path.of(project.getBuild().getOutputDirectory());
            write(out.resolve(Stamp.resource(self.group(), self.artifact())), self.text());

            List<Stamp> stack = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Artifact a : project.getArtifacts()) {
                for (Stamp s : Bill.stampsIn(a.getFile() == null ? null : a.getFile().toPath())) {
                    if (seen.add(s.group() + ":" + s.artifact())) {
                        stack.add(s);
                    }
                }
            }
            Bill bill = new Bill(self, stack);
            Path billFile = out.resolve(Bill.RESOURCE);
            String before = Files.isRegularFile(billFile) ? Files.readString(billFile, StandardCharsets.UTF_8) : null;
            write(billFile, bill.text());
            if (before != null && !before.equals(bill.text())) {
                forgetIncrementalState();
            }

            project.getProperties().setProperty("vexelray.build.source", self.source());
            project.getProperties().setProperty("vexelray.build.commit", self.commit());
            getLog().info("build identity: " + self.line() + (stack.isEmpty() ? "" : ", and " + stack.size()
                    + " stamped part" + (stack.size() == 1 ? "" : "s") + " on the class path"));
        } catch (IOException e) {
            throw new MojoExecutionException("could not work out the build identity of " + project.getArtifactId()
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * The bill changed, so whatever was generated from the old one is stale, even if no source file changed (a new
     * commit that only touched a README, or a rebuilt stack module). The framework's code generator reads the bill
     * while compiling, and the compiler skips a module whose sources are unchanged. Forgetting its incremental state
     * makes it compile everything again, which it does once per new identity.
     */
    private void forgetIncrementalState() throws IOException {
        Path status = Path.of(project.getBuild().getDirectory(), "maven-status", "maven-compiler-plugin");
        if (Files.isDirectory(status)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(status)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
            getLog().info("build identity changed: the next compile starts from scratch, so it is baked in afresh");
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
