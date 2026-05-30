# Repository Instructions

## Release Process

Use semantic versioning for releases. Android releases must update both values in
`app/build.gradle.kts`:

- `versionName`: release version without a prefix, for example `1.5.2`
- `versionCode`: increment the previous integer value

Update `CHANGELOG.md` before publishing a release.

The final release commit must follow this exact naming convention:

```text
Release X.Y.Z
```

Example:

```text
Release 1.5.2
```

Create an annotated Git tag pointing to that final release commit:

```bash
git tag -a vX.Y.Z -m "Release X.Y.Z"
git push origin master
git push origin vX.Y.Z
```

Example:

```bash
git tag -a v1.5.2 -m "Release 1.5.2"
git push origin master
git push origin v1.5.2
```

The workflow `.github/workflows/build-release.yml` must be triggered only by
tags matching `v*` or manually through `workflow_dispatch`. Do not trigger
release workflows on regular pushes to `master`, otherwise GitHub Actions shows
duplicate release runs.

Before pushing a release tag, verify that it does not already exist locally or
remotely:

```bash
git tag --list "vX.Y.Z"
git ls-remote --tags origin "refs/tags/vX.Y.Z"
```

Do not move or force-update a published release tag unless explicitly required
to repair an incorrect release.
