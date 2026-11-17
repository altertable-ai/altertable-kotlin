#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to verify both SDK publications}"
verification_dir=$(mktemp -d)
cleanup() {
  gpgconf --kill gpg-agent || true
  rm -rf "$verification_dir"
}
trap cleanup EXIT
export GNUPGHOME="$verification_dir/gnupg"
mkdir -m 700 "$GNUPGHOME"
gpg --batch --pinentry-mode loopback --passphrase release-verification \
  --quick-generate-key 'Release Verification <release-verification@example.invalid>' rsa2048 sign 1d
export ORG_GRADLE_PROJECT_signingInMemoryKey
ORG_GRADLE_PROJECT_signingInMemoryKey=$(gpg --batch --pinentry-mode loopback \
  --passphrase release-verification --armor --export-secret-keys)
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=release-verification
./gradlew :altertable:publishToMavenLocal :altertable-android:publishToMavenLocal \
  -Dmaven.repo.local="$verification_dir/maven" "$@"
python3 scripts/verify-release.py "$verification_dir/maven" --signed
while IFS= read -r -d '' signature; do
  gpg --batch --verify "$signature" "${signature%.asc}"
done < <(find "$verification_dir/maven" -name '*.asc' -print0)
