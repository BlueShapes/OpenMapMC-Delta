# Contributing

Thanks for contributing to [OpenMapMC Delta](https://github.com/BlueShapes/OpenMapMC-Delta).
Bug reports, documentation improvements, translations, and code changes are all welcome.

## Before you start

- An issue or discussion can help coordinate a substantial change, but you can also open a pull request directly.
- Keep changes focused. Avoid unrelated refactors in the same pull request.
- Preserve compatibility-sensitive identifiers (`modId`, package names, registry IDs) unless a change explicitly requires them.

## Development workflow

1. Fork the repository and create a branch from `main`.
2. Make the smallest coherent change that solves the problem.
3. Use the matching JDK and build the loader you changed inside its version root. The [README](README.MD#building) lists the commands.
4. Update documentation when behavior, configuration, or release steps change.
5. Open a pull request with a clear summary, verification notes, and any compatibility risks.

## Coding expectations

- Follow the existing patterns in the targeted version root. The version folders are intentionally isolated.
- Version roots are independent copies. When a change applies to multiple Minecraft versions, adapt it to each version's APIs and describe which versions you checked.
- Avoid committing generated jars, logs, run directories, secrets, or local IDE files.

## Verification

Start with the smallest relevant compile, build, or standalone test. Most focused
PowerShell runners are under `scripts/`, with fixtures under `tests/`. Building
every Minecraft version or launching the game is not expected for every change.
If a change needs runtime testing, describe the environment and the result in
the pull request. Clearly note checks you could not run.

Use your own development environment and preferred editor. Personal assistant
settings and deployment configuration can stay local. No particular Git
identity, commit-signing setup, automation tool, or launcher instance is
required to contribute.

## Reporting issues

- Use [GitHub issues](https://github.com/BlueShapes/OpenMapMC-Delta/issues) for bug reports and feature requests.
- Include the Minecraft version, loader, mod version, reproduction steps, and relevant logs when possible.
- Redact tokens, passwords, personal file paths, and other private information before sharing logs.
- Report security-sensitive findings using the [security policy](.github/SECURITY.md).

By contributing, you agree that your contributions are licensed under the Apache License 2.0 unless stated otherwise.
