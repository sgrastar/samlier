# Samlier

Samlier is an open-source black-box conformance suite for SAML implementations. Version 0.1 targets the Kantara SAML V2.0 Implementation Profile for Federation Interoperability v1.1 and tests both identity providers and service providers.

The implementation includes the signed G1 obligation catalog, independently reviewed G2 case design, all approved M1–M3 execution workflows, SSO/SLO/ECP protocol peers, metadata and MDQ variants, a secondary IdP, redacted transcripts, schema-v1 result JSON, and self-contained HTML reports. A result can remain `INDETERMINATE` or `INCOMPLETE` when target configuration or external evidence is unavailable; Samlier does not hide those obligations as not applicable.

## Requirements

- Java 21
- Node.js 24 (Node.js 20.19 or later is also supported by Vite)
- Docker, optionally

## Run from source

```bash
./gradlew check
SAMLIER_IMAGE_DIGEST="sha256:<digest-of-the-build-you-are-running>" \
SAMLIER_DATA_DIR="$PWD/data" ./gradlew :api:run
```

Open <http://localhost:8080>. Runtime state, generated Test Peer keys, cached target metadata, and transcripts are stored below `SAMLIER_DATA_DIR`.

Before publishing a release or container, configure the immutable G1/G2 tool pins and SSH
allowed-signers file, then run `./gradlew releaseCheck`. This gate force-reconciles the source
specifications, validates both signed approvals through externally pinned verifiers, and runs the
complete backend and web test suite. Container publication must not bypass this task.

Generated Test Peer private keys are stored unencrypted below that directory. They are test-only keys and must never be trusted by production systems. Run Samlier only against systems you own or are authorized to test.

## Run with Docker

```bash
docker build -t samlier:0.1.0 .
IMAGE_DIGEST="$(docker image inspect --format '{{.Id}}' samlier:0.1.0)"
docker run --rm -p 8080:8080 -v samlier-data:/data \
  -e SAMLIER_PUBLIC_BASE_URL=http://localhost:8080 \
  -e SAMLIER_PEER_BASE_URL=http://localhost:8080 \
  -e SAMLIER_IMAGE_DIGEST="$IMAGE_DIGEST" \
  samlier:0.1.0
```

Self-hosted mode permits private network targets by default. Hosted deployments must use separate application and Test Peer origins and block private or special-purpose outbound destinations.

Only test systems you own or are explicitly authorized to test. The UI and API require that confirmation when a Test Plan is created. Self-hosted mode has no application authentication and must not be exposed directly to an untrusted network; put an authentication proxy in front of it when remote access is necessary.

Self-hosted results are local, self-declared exports. They cannot be uploaded and converted into an official shared URL. Shared URLs are issued only for Runs executed by a Hosted Samlier deployment. This is a test result, not a certification, and neither Kantara nor OASIS endorses an individual result.

## Keycloak smoke test

`dev/keycloak/prepare-smoke.sh` starts the pinned Keycloak fixture and Samlier image, imports the generated Test Peer metadata, creates a Run, completes the Keycloak login and SAML POST, and fails unless the Run reaches `COMPLETED` with round-trip evidence. Set `SAMLIER_SMOKE_MANUAL=1` to print the browser URL instead. Continue from the Run management page to execute M1–M3 evidence workflows. The smoke round trip alone is an operational check, not a conformance determination.

## Project status and design

The requirements catalog and role-specific case design are protected by signed G1/G2 approval records. The implementation registry is tested against all approved case IDs so a missing case fails the build. See [the design index](docs/README.md), [the G2 design](docs/12-g2-test-design.md), [the roadmap](docs/01-scope-and-roadmap.md), and [the implementation rules](AGENTS.md).

## License

Apache License 2.0. Contributions use the Developer Certificate of Origin; no CLA is required.
