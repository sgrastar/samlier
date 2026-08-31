# Samlier

Samlier is an open-source black-box conformance suite for SAML implementations. The current release targets the [Kantara SAML V2.0 Implementation Profile for Federation Interoperability v1.1](docs/04-requirement-coverage.md) and exercises both identity providers (IdPs) and service providers (SPs).

Samlier is deliberately not a questionnaire with a verdict attached. It drives standard SAML endpoints, supplies controlled metadata, records redacted Transcripts, and derives case outcomes from observable evidence. A small set of properties that cannot be established through standard external interfaces remains clearly labelled as self-attested. Missing evidence becomes `NOT_VERIFIED`; it is never silently removed from scope or converted into a target failure.

## What Samlier tests

The signed requirement catalog covers the following areas:

- SAML request and response processing, bindings, identifiers, assertions, signatures, and encryption
- browser SSO, unsolicited responses, ForceAuthn, IsPassive, NameIDPolicy, ACS selection, and proxy processing
- single logout, ECP, channel binding, and SAML Enhanced Client extensions
- metadata acquisition, signature and trust handling, refresh behavior, key rollover, MDQ, discovery, UI information, and algorithm declarations
- cryptographic algorithm capabilities and policy
- IdP- and SP-specific Core and Full profile obligations

Every approved case returns an `outcome`, never a verdict. The central Evaluator combines that outcome with the approved requirement level to produce `PASS`, `WARNING`, `FAIL`, or an unresolved result. See [the test model](docs/03-test-model.md) and [the case format](docs/05-test-definition-format.md).

## Evidence plans

The Run page presents three cumulative plans. The plan controls evidence depth, not a different interpretation of conformance.

| Plan | Evidence included | What the operator does |
|---|---|---|
| **Quick** | Protocol-observed evidence | Follow SAML login, consent, logout, or Continue steps when prompted. Samlier judges the resulting SAML, browser result, metadata, and Transcript. |
| **Standard** | Quick plus operator-assisted evidence | Make a requested configuration change or refresh/re-import the stable Suite metadata URL. Samlier still determines the outcome; the operator never enters PASS or FAIL. |
| **Full** | Standard plus self-attested evidence | Review grouped evidence sections only for internal behavior that standard SAML surfaces cannot prove. Self-attested results remain separate from externally verified results. |

One login or metadata operation can supply evidence to several approved cases. The UI therefore reports both case coverage and deliberate user actions. Standard metadata fixtures are presented as a work queue. For a target that periodically retrieves metadata, or refreshes it when it encounters an unknown signing key, the operator configures one Run-scoped polling URL and starts one signed browser campaign. Samlier uses a distinct deterministic test key per fixture and keeps that fixture stable across duplicate target fetches. It advances after the correlated browser flow returns to Samlier, so a target that reloads the same URL more than once cannot skip fixtures. An operator-configurable delay lets the target's ordinary key-refresh window elapse; the delay affects orchestration only, never the target outcome. If the target keeps the browser on its own result page, the Run page provides one continuation action after the attempted flow. That action advances orchestration but does not claim that metadata was fetched or used and supplies no conformance outcome; missing observations remain `NOT_VERIFIED`. For static-import products that support an `EntitiesDescriptor` containing multiple SP entities, Samlier can instead publish compatible positive fixtures as one downloadable signed aggregate: import one XML file, then start one browser sequence that reuses the target session and records a correlated response for each entity. Downloading the file is not Target-fetch evidence; only a Target fetch or a correlated SAML response can prove external use. Document-wide and negative fixtures (for example, an invalid signature, duplicate `entityID`, or an expired root) remain separate because combining them would destroy the test oracle. Products that support neither polling nor aggregate import retain the manual queue. Samlier does not use a vendor administration API as a conformance oracle.

Browser-assisted protocol scenarios also share real session operations. A normal target login is counted once across cases that reuse the same IdP session; forced reauthentication is a separate action, and all fixtures that require an empty browser session share one post-boundary session-recovery action. The active-probe chain still preserves every case's own controls and Transcript correlation. For local algorithm policy that has no SAML management protocol, compatible cases share two explicit policy families—content encryption and key transport—instead of presenting one completion button per case. Applying a policy family does not assert conformance: if no external Transcript proves the required algorithm behavior, every affected case remains `NOT_VERIFIED`.

The action budgets are targets, not hidden pass criteria. A Standard Run can exceed its budget when the target neither polls metadata automatically nor exposes a product-neutral refresh mechanism: Samlier can remove repeated fixture selection and reuse one observation across many cases, but it cannot honestly claim that a target-side refresh occurred when it did not. The Run page reports that excess explicitly rather than disguising it as automation. Automatic polling removes repeated re-imports; correlated SAML flows are still required where the approved case needs proof that the fetched metadata was actually used. The displayed budget starts with one campaign-start action and adds every Target-result continuation actually used, so a product that stops on several rejection fixtures is not reported with an unrealistically optimistic interaction count.

## Implementation status

| Area | Status |
|---|---|
| G1 requirements catalog and source reconciliation | Implemented and signed |
| G2 case design, controls, mutants, and interaction budgets | Implemented and signed |
| M0 application, persistence, peer metadata, and round trip | Implemented |
| M1 browser SSO and core protocol workflows | Implemented |
| M2 metadata, MDQ, trust, and rollover workflows | Implemented |
| M3 SLO, ECP, channel-binding, and extension workflows | Implemented |
| Quick / Standard / Full campaign orchestration | Implemented; external-observation coverage continues to expand |
| Static HTML and schema-v1 JSON reports | Implemented |
| Hosted result publication | Implemented; deployment policy and independent certification remain separate concerns |

The implementation registry is checked against every approved G2 case ID, and the mutant fixtures test the Suite's detection rules rather than the target. Some Full-profile capabilities are uncommon or impossible to observe from outside a product; those remain explicit `SELF_ATTESTED` or `NOT_VERIFIED` evidence instead of being guessed.

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

## Run a test

1. Open <http://localhost:8080> and create a Test Plan.
2. Select the target role/profile, enter the target entity ID and metadata URL, and confirm that you are authorized to test it.
3. Run **Preflight**. Samlier snapshots the target metadata and identifies reachable standard endpoints.
4. Register the Test Peer metadata shown on the Run page in the target. For metadata-consumer tests, use the displayed Run-scoped stable metadata URL.
5. For an IdP, start the initial IdP round trip and log in with a non-production test account. For an SP, initiate login at the target SP using the Samlier IdP metadata.
6. Select the desired evidence depth:
   - run or resume M1 for browser SSO and core protocol evidence;
   - run or resume M2 for metadata and trust evidence;
   - run or resume M3 for logout and extension evidence.
7. Follow only the operation prompts shown by the selected plan. A protocol-driven case completes when correlated evidence arrives. Use an unavailability answer only when the required setup genuinely cannot be exercised.
8. Open the current report, then export `result.json` or the self-contained `report.html`.

Use a fresh/private browser context when a campaign says that session isolation is required. A successful login alone is not a conformance result: positive and negative controls must both have enough evidence before the corresponding case can complete.

### Reading a result

- **Externally verified** means the outcome was derived from Suite-observed SAML, browser, metadata, or Transcript evidence.
- **Self-attested** means the target operator supplied evidence about behavior that Samlier could not prove externally.
- **Not verified** means the required observation or configuration was unavailable or inconclusive.
- **Incomplete** means applicable mandatory obligations remain unresolved.
- A published report is a test result, not a certification, and neither Kantara nor OASIS endorses an individual result.

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

## Keycloak acceptance fixture

`dev/keycloak/prepare-smoke.sh` starts the pinned Keycloak fixture and Samlier image, imports the generated Test Peer metadata, creates a Run, completes the Keycloak login and SAML POST, and fails unless the Run reaches `COMPLETED` with round-trip evidence. Set `SAMLIER_SMOKE_MANUAL=1` to print the browser URL instead. Continue from the Run management page to exercise the full Quick, Standard, or Full workflows. Keycloak-specific provisioning prepares the disposable fixture only; it is not evidence and is never used as the conformance oracle.

On macOS, the same acceptance fixture can run with Apple Container instead of Docker:

```bash
python3 dev/keycloak/prepare-smoke-apple.py --manual
```

The launcher builds the current checkout, records the actual OCI image digest in the Run, starts both services on an isolated Apple Container network, and uses Keycloak's network address for target metadata retrieval. Use `--no-build` only when the named local image already represents the checkout being tested.

During an acceptance Run, Samlier derives outcomes directly from the Run-scoped metadata snapshot and
validated SAML Transcripts where the approved case has a complete observation rule. The operator still
uses the target's normal administration UI to register metadata or select a required configuration, but
does not answer `PASS` or `FAIL` for those evidence-driven cases. Cases that depend on internal product
state or on a configuration that cannot be exercised remain explicit configuration or attestation
interactions; unavailable evidence is reported as `NOT_VERIFIED` rather than target nonconformance.

## Development and verification

Run the complete local test suite:

```bash
./gradlew check
```

The build includes unit tests for outcome mapping, positive/negative controls, campaign sharing, fresh-session boundaries, fixture progression, evidence-class reporting, transcript redaction, result generation, and API/UI behavior. G1 and G2 have separate validators and externally pinned signed-approval checks; see [AGENTS.md](AGENTS.md) for the required commands and safety rules.

Before publishing a release or OCI image, configure the immutable G1/G2 verifier commits and SSH allowed-signers file, then run:

```bash
./gradlew releaseCheck
```

This gate force-reconciles source specifications, verifies both signed approvals through pinned tools, verifies dependency metadata, and runs the backend and web test suites. Publication must not bypass it.

## Design and security notes

The requirements catalog and role-specific case design are protected by signed G1/G2 approval records. The implementation registry is tested against all approved case IDs so a missing case fails the build. See [the design index](docs/README.md), [the G2 design](docs/12-g2-test-design.md), [the roadmap](docs/01-scope-and-roadmap.md), [the Suite security design](docs/08-suite-security.md), and [the implementation rules](AGENTS.md).

## License

Apache License 2.0. Contributions use the Developer Certificate of Origin; no CLA is required.
