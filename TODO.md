# vexelray-build — TODO

## Separate build output for each machine sharing a checkout

The suite is developed from one checkout seen by two machines: Windows, and a Linux VM reading it through a VMware
shared folder. Both write `target/` in the same place, so whichever built last owns every module's output. Switching
machines means a `clean install` of every repo in dependency order, and forgetting one gives errors that look like
real bugs (a stale class from the other OS, a `NoSuchMethodError` against an older jar in `~/.m2`).

Give each OS its own output directory, chosen in one place every repo already inherits or applies: for example
`<directory>${project.basedir}/target/${os.detected}</directory>` (or a `target-linux/` sibling) set by a profile
activated per OS family, the same activation the OS modules use. Check what that breaks before turning it on:
plugins and scripts that hard-code `target/` (the msdf plugin's outputs, `native/build.sh` in ffmpeg-wrapper and
imagelib-wrapper, the installers' `installer.json` paths, `.gitignore` entries) all need to follow it. `~/.m2` is
already per machine, so only the in-tree output is shared.
