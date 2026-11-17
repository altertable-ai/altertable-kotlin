# Release the SDKs

Release Please keeps `sdk.version` in `gradle.properties`, `.release-please-manifest.json`, and `CHANGELOG.md` in sync. Each release publishes both artifacts to Maven Central:

- `ai.altertable.sdk:altertable-kotlin`
- `ai.altertable.sdk:altertable-android`

## Publishing setup

Use a [Central Portal account](https://central.sonatype.com/) with a verified namespace covering `ai.altertable.sdk`, a [Portal user token](https://central.sonatype.org/publish/generate-portal-token/), and a passphrase-protected PGP signing key with its public key distributed to a keyserver.

Create the `maven-central` GitHub environment with a required reviewer:

```fish
read --prompt-str 'GitHub release reviewer username: ' release_reviewer; and gh api users/$release_reviewer --jq .id | jq -s '{reviewers: [{type: "User", id: .[0]}], deployment_branch_policy: {protected_branches: false, custom_branch_policies: true}}' | gh api --method PUT repos/altertable-ai/altertable-kotlin/environments/maven-central --input -
```

Restrict deployments to `main`:

```fish
gh api --method POST repos/altertable-ai/altertable-kotlin/environments/maven-central/deployment-branch-policies -f name=main -f type=branch
```

Configure these environment secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | Portal user-token password |
| `SIGNING_KEY` | Complete ASCII-armored private key, including header, footer, and newlines |
| `SIGNING_PASSWORD` | Private key passphrase |

Enter the token credentials and key passphrase at the CLI prompts:

```fish
gh secret set MAVEN_CENTRAL_USERNAME --repo altertable-ai/altertable-kotlin --env maven-central
```

```fish
gh secret set MAVEN_CENTRAL_PASSWORD --repo altertable-ai/altertable-kotlin --env maven-central
```

```fish
gh secret set SIGNING_PASSWORD --repo altertable-ai/altertable-kotlin --env maven-central
```

Upload the private key directly:

```fish
read --prompt-str 'Signing key fingerprint: ' release_signing_fingerprint; and gpg --armor --export-secret-keys $release_signing_fingerprint | gh secret set SIGNING_KEY --repo altertable-ai/altertable-kotlin --env maven-central
```

Distribute its public key:

```fish
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys $release_signing_fingerprint
```

## Local verification

Install JDK 21 and Android SDK platform 35 with build tools 35.0.0. With the standard macOS SDK location, run:

```fish
env ANDROID_HOME="$HOME/Library/Android/sdk" bash scripts/verify-release.sh --console=plain
```

This uses a disposable signing key and local Maven repository to verify both artifacts, documentation, versions, and signatures without uploading packages. CI runs the same check.

## Release workflow

Pushes to `main` update the release PR. Because Release Please uses `GITHUB_TOKEN`, run CI explicitly on its branch before merging:

```fish
gh workflow run ci.yml --repo altertable-ai/altertable-kotlin --ref release-please--branches--main
```

Review the changelog and shared version, then merge the release PR. Release Please creates the GitHub release and tag. Approve the `maven-central` deployment to publish both SDKs from that tag.

For a failed publication, inspect [Central Portal deployments](https://central.sonatype.com/publishing/deployments) and resolve the failed deployment first. Confirm the version is unpublished before retrying, since Central versions are immutable:

```fish
read --prompt-str 'Release tag to retry: ' release_tag; and gh workflow run release-please.yml --repo altertable-ai/altertable-kotlin --ref main -f release_tag=$release_tag
```
