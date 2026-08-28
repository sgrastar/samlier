# Samlier

Samlier is an open-source black-box conformance suite for SAML implementations. It is being built around the Kantara Interoperability Implementation Profile and tests both identity providers and service providers.

The current M0 implementation is a zero-conformance-case skeleton. It provides Test Plan CRUD, preflight checks, signed Test Peer metadata, SAML browser round trips, redacted transcripts, and live Run events. It does **not** make conformance determinations yet.

## Requirements

- Java 21
- Node.js 24 (Node.js 20.19 or later is also supported by Vite)
- Docker, optionally

## Run from source

```bash
./gradlew check
SAMLIER_DATA_DIR="$PWD/data" ./gradlew :api:run
```

Open <http://localhost:8080>. Runtime state, generated Test Peer keys, cached target metadata, and transcripts are stored below `SAMLIER_DATA_DIR`.

Generated Test Peer private keys are stored unencrypted below that directory. They are test-only keys and must never be trusted by production systems. Run Samlier only against systems you own or are authorized to test.

## Run with Docker

```bash
docker build -t samlier:m0 .
docker run --rm -p 8080:8080 -v samlier-data:/data \
  -e SAMLIER_PUBLIC_BASE_URL=http://localhost:8080 \
  -e SAMLIER_PEER_BASE_URL=http://localhost:8080 \
  samlier:m0
```

Self-hosted mode permits private network targets by default. Hosted deployments must use separate application and Test Peer origins and block private or special-purpose outbound destinations.

## Keycloak M0 smoke test

`dev/keycloak/prepare-smoke.sh` starts the pinned Keycloak fixture and Samlier image, imports the generated Test Peer metadata, creates a Run, and prints the browser start URL. Complete the login with the fixture credentials printed by the script; the Run must finish as `COMPLETED`. This is an operational smoke test, not a conformance determination.

## Project status and design

The requirements catalog has passed signed G1 review and the zero-case M0 skeleton is implemented. The role-specific G2 case and mutant design is awaiting independent signed approval. No verdict cases are implemented; M1 remains blocked until G2 approval. See [the design index](docs/README.md), [the G2 design](docs/12-g2-test-design.md), [the roadmap](docs/01-scope-and-roadmap.md), and [the implementation rules](AGENTS.md).

## License

Apache License 2.0. Contributions use the Developer Certificate of Origin; no CLA is required.
