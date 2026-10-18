# vexelray-build

A Maven plugin that stamps every module of the VexelRay stack with what its binaries were built from. When it builds
an application, it also gathers the stamps of everything on the application's class path, so a running application
knows the exact source of every part of itself.

**Build it first**, before every repo it stamps:

```
cd vexelray-build && mvn install
```

## Why

A version does not say what a binary was built from. `0.1.1-SNAPSHOT` covers every commit between two releases, and
every uncommitted edit on top of any of them. The Inspector keeps statistics by build so a regression shows at a
glance (`vexelray-inspector/docs/roadmap.md`), and that only works if two builds with the same identity really were
built from the same files.

The identity is therefore the source itself:

- **The commit.** The full hash of `HEAD` in the module's git checkout.
- **The uncommitted changes.** When the working tree differs from `HEAD`, a digest of exactly the difference: SHA-256
  over `git diff HEAD --binary`, then every untracked file that is not ignored, as its path and its bytes. Ignored
  files such as `target/` are not source. The digest covers the whole repository, because a module's binaries also
  depend on its parent pom and its siblings. That can make two identical builds look different, never the reverse.
- **The parts.** An application's binaries also change when a stack module changes, even if the application's own
  code did not. So every stack module carries its own stamp, and the application's bill lists them all.

A source reads `ef816d9…` for a clean tree, or `ef816d9…+dirty.0a1b2c3d4e5f` with uncommitted changes.

## What it writes

The goal `identify` runs at `initialize`, before anything compiles, and writes two files into the module's output
(so both go into its jar):

| File | What it holds |
| --- | --- |
| `META-INF/vexelray/build/<groupId>/<artifactId>.properties` | **The stamp**: `group`, `artifact`, `version`, `commit`, `dirty`, `digest`, `source`. One per module, so jars never collide. |
| `META-INF/vexelray/build.properties` | **The bill**: the stamp, then `stack.count` and `stack.<n>=group:artifact:version@source` for every stamped part on the runtime class path. |

It also sets `vexelray.build.source` and `vexelray.build.commit` for the rest of the build. A module whose packaging
is `pom` gets neither file. Outside a git checkout, or without git on the `PATH`, the commit is `unknown`.
`-Dvexelray.build.strict=true` fails the build instead, and `-Dvexelray.build.skip=true` skips a module.

## Using it

In a repo's parent pom, or an application's pom:

```xml
<plugin>
    <groupId>dev.vexelray.build</groupId>
    <artifactId>vexelray-build-maven-plugin</artifactId>
    <version>0.1.1-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>identify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The plugin is versioned with the rest of the stack: `vexelray-dist/release.py` builds it first and moves it, and every
pom that names it, to each release and next snapshot along with the other repositories.

An application on `vexelray-framework` needs nothing more. The framework's code generator reads the bill while the
application compiles and bakes it into the generated wiring, so there is nothing to read at run time, and nothing a
native image has to be told about. The automation socket then advertises the build in the discovery file and in
`hello` (`vexelray-gui/docs/plans/discovery.md`), and the Inspector keeps its history by it.

## Notes

- **Java 21, not 25.** `maven-plugin-plugin` 3.15 reads the compiled classes to build the plugin descriptor, and its
  ASM does not know class version 69 yet. `vexelray-msdf-maven-plugin` does the same.
- **Build time is not part of the identity.** Two builds of the same source are the same build.
