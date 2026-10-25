# vexelray-build

A Maven plugin that stamps every module of the VexelRay stack with what its binaries were built from. When it builds
an application, it also gathers the stamps of everything on the application's class path, so a running application
knows the exact source of every part of itself.

Part of the [VexelRay](https://sibarum.github.io/) stack. Building an application on it? Start at the [developer guide](https://sibarum.github.io/vexelray-framework/developers/).

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
| `META-INF/vexelray/build/<groupId>/<artifactId>.properties` | **The stamp**: `group`, `artifact`, `version`, `commit`, `dirty`, `digest`, `source`, and `issues` when the pom has an `<issueManagement><url>` (inherited, like the rest of the model). One per module, so jars never collide. |
| `META-INF/vexelray/build.properties` | **The bill**: the stamp, then `app.id` (see below), `name`, `built` and `notices` (see *What an About window reads*), then `installer.id` and `installer.replaces` when there is an `installer.json`, then `stack.count` and `stack.<n>=group:artifact:version@source` for every stamped part on the runtime class path, each followed by `stack.<n>.issues` when that part names a tracker. |

It also sets `vexelray.build.source` and `vexelray.build.commit` for the rest of the build. A module whose packaging
is `pom` gets neither file. Outside a git checkout, or without git on the `PATH`, the commit is `unknown`.
`-Dvexelray.build.strict=true` fails the build instead, and `-Dvexelray.build.skip=true` skips a module.

### The application id

`app.id` is the name an application's windows go by on the desktop: `WM_CLASS` on X11, the `app_id` on Wayland. A
taskbar or dock matches it against the name of the application's desktop entry to show the right icon, so the installer
names the desktop entry after it too. It defaults to `<groupId>.<artifactId>` (`dev.sibarum.pix`). Set the property
`vexelray.app.id` in the application's pom to override it, for coordinates that are awkward as a name, or to keep the
name an application was first installed under after its coordinates change. Changing it renames the installed desktop
entry, and a dock forgets any pin on the old one.

It has to be a desktop entry name: dot-separated parts of letters, digits, `_` and `-`, none starting with a digit, at
least two parts. When an `installer.json` sits beside the pom, `identify` checks that its `appId` is the same, and that a
config with a `linux` section declares one, and fails the build if not. That keeps the binary and the installer from
drifting apart, since the installer never sees the pom.

### The installer's id and the application's name

An application finds its own install record (for its update check) and its settings directory by its `@VexelApp`
name, and the installer records the install under the `id` in `installer.json`, so the two must be the same. `identify`
reads that `id` and the ids it `replaces` into the bill, and the framework's code generator fails the compile when the
`@VexelApp` name differs, or when an id the installer replaces is missing from `@VexelApp(formerNames)`. A former name
the installer does not replace is fine: only the settings directory was renamed. Applications not on the framework
skip the check.

### What an About window reads

The framework's About window (`vexelray-framework`, opened from the mark at the left of the title bar) shows the bill,
so everything on it is a fact of the build rather than a string an application keeps up to date by hand:

| Key | What it is |
| --- | --- |
| `name` | the application's proper name: the `name` in its `installer.json` (the name on its shortcuts), else the pom's `<name>`, else the artifactId |
| `built` | when this identity was first built into this output directory (a `clean` starts over), an ISO-8601 instant in UTC. Not part of the identity: a rebuild of the same source keeps the time already in the bill, so the bill on disk does not change and the compiler can still skip a module whose identity did not move |
| `notices` | where the third-party notices of this build are read: the release asset `https://github.com/<releasesRepo>/releases/download/<tagFormat>/THIRD-PARTY-NOTICES.txt`, when the module has a `THIRD-PARTY-NOTICES.txt` beside its pom and an `installer.json` naming its releases. `-Dvexelray.notices.url` (or the property in the pom) names another. Absent when neither |
| `stack.<n>.issues` | where that part's problems are reported, from its pom's `<issueManagement>`. The framework's is the About window's *Report a problem* |

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

An application that is one module of a repository, with its `installer.json` and `THIRD-PARTY-NOTICES.txt` at the
repository's root rather than beside its own pom, names that `installer.json` in the execution, and the notices are then
looked for beside it (`-Dvexelray.installer.config` does the same from the command line):

```xml
<configuration>
    <installerConfig>${project.basedir}/../installer.json</installerConfig>
</configuration>
```

Without it, `identify` sees no installer: the About window falls back to the pom's `<name>` and has no notices to open,
and the check that the `@VexelApp` name is the installer's id never runs.

An application on `vexelray-framework` needs nothing more. The framework's code generator reads the bill while the
application compiles and bakes it into the generated wiring, so there is nothing to read at run time, and nothing a
native image has to be told about. The automation socket then advertises the build in the discovery file and in
`hello` (`vexelray-gui/docs/plans/discovery.md`), and the Inspector keeps its history by it.

## Build output on Linux

Every repository's root pom has a `linux-build-output` profile, activated on Linux, that moves `project.build.directory`
to `target-linux/`. The suite is developed from one checkout that a Windows host and a Linux VM both build (a VMware
shared folder), and this lets each keep its output beside the other's rather than overwrite it, so moving between
them needs no clean rebuild. Windows and macOS still build into `target/`.

What follows from it:

- anything that reads build output asks Maven where it is (`${project.build.directory}`), or, from code, names the
  directory its own classes were loaded from; nothing names `target/` in a path;
- applications run by `exec:exec` are passed `-Dbuild.dir=${project.build.directory}`, which is where atchung puts
  their logs: `target-linux/logs/` on Linux;
- `.gitignore` lists `target-linux/` beside `target/`, which keeps the source digest above clean;
- imagelib's Linux `native/build.sh` gives cargo `native/imagelib/target-linux` for the same reason.

New repositories copy the profile from any root pom; `vexelray-framework`'s template has it.

## Notes

- **Java 21, not 25.** `maven-plugin-plugin` 3.15 reads the compiled classes to build the plugin descriptor, and its
  ASM does not know class version 69 yet. `vexelray-msdf-maven-plugin` does the same.
- **Build time is not part of the identity.** Two builds of the same source are the same build, which is why `built`
  is the time the identity was first built and not the time of the last `mvn` run.
