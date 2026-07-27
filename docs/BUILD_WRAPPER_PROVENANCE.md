# Gradle wrapper provenance — please verify before first use

`gradle-wrapper.jar` is a **binary committed to this repo**, so it deserves an explicit provenance
note rather than silent trust.

## What was done

- Downloaded `gradle-8.7-bin.zip` from `https://services.gradle.org/distributions/` over HTTPS.
- Extracted it and ran `gradle wrapper --gradle-version 8.7 --gradle-distribution-sha256-sum <sha>`,
  which is why `gradle-wrapper.properties` carries `distributionSha256Sum` — the wrapper will now
  verify the Gradle distribution itself on every run, on any machine.

## Checksums recorded

| Artifact | SHA-256 |
|---|---|
| `gradle-8.7-bin.zip` | `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d` |
| `gradle/wrapper/gradle-wrapper.jar` (identical in both apps) | `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8` |

## ⚠️ Honest limitation

**I could not verify the distribution against Gradle's published checksum from this environment.**
The outbound proxy serves `.zip` but returns HTTP 403 for `gradle-8.7-bin.zip.sha256`, and the
proxy performs TLS interception — so "fetched over HTTPS from gradle.org" is not an end-to-end
guarantee here. The zip's structural integrity was checked (`unzip -t`) and the resulting Gradle
reported `Gradle 8.7`, but neither of those proves authenticity.

**Before trusting this wrapper, verify it yourself** (30 seconds, on a machine with unrestricted
network):

```sh
curl -sSL https://services.gradle.org/distributions/gradle-8.7-bin.zip.sha256
# must print: 544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d
```

If it does not match, delete the committed wrapper and regenerate it locally:

```sh
cd packages/apps/OIWCamera && gradle wrapper --gradle-version 8.7
```

Doing that is also perfectly reasonable as a default policy — many teams decline to trust any
committed wrapper jar they did not generate themselves.
