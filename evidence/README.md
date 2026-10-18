# Evidence: Supervisor logs from the re-verification runs

Supervisor stdout (`docker logs nmf-sup`) from the 2026-10-06 runs, plus one
2026-10-18 re-run on each ref. One file per run, for `release-5.1` (`5.1/`) and `v6.0`
(`6.0/`). The only
trimming is that Java stack-frame lines (`\tat ...`) are removed. The
`f2-deeptraversal-escape` files are the exception: they were captured as a
filtered excerpt to begin with.

| Run | Findings | What to look for |
|---|---|---|
| `linux-userspace-f1-f6-f8.log` | 1, 6, 8 | Two installs of `hello-world`, the same `adduser` run twice, the `chgrp ... /nonexistent` error, and `classified as CRASHED`. For 8, only the successful metadata-name `runApp`; the failing filename `listApp` is client-side |
| `linux-userspace-f2.log` | 2 | `Creating file on: .../apps/../../outside-target/...`, the `.receipt` `FileNotFoundException` |
| `f2-deeptraversal-escape.log` | 2 | Already-filtered excerpt (`grep` of the relevant lines) for the `../../../../tmp/nmf-escape` package and the refused uninstall |
| `fresh-linux-userspace-f8-f1-f6-f2.log` | 8, 1, 6, 2 | The 2026-10-18 clean-room re-run: `hello-world` installs, then both traversal packages logging `Package successfully installed` with no `SEVERE`, because their receipts were written |
| `f3-testA.log`, `f3-testB.log` | 3 | A normal-looking boot, then a plain `[/bin/sh, -c, ...]` launcher instead of `[sudo, su, -, app-...]` |
| `bubblewrap-f4-f7.log` | 4, 7 | The full `bwrap` launcher command (`--ro-bind, /, /`, `PATH, /usr/local/bin:/usr/bin:/bin`) |
| `bubblewrap-exitcode-masking.log` | 10 | `exited with code 0, classified as EXITED` for runs whose `.exit` file held 127 and 1 |
| `docker-containers-f5-f9.log` | 5, 9 | The `[docker, run, ...]` command with no resource flags, and exit code 125 |

Not in these files: anything observed with `docker exec` (`/etc/passwd`,
directory listings, `ps`), App logs, `.exit` files, and the `RunAppTrigger`
client output. Each finding's Observed section quotes those directly.
