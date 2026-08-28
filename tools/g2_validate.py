#!/usr/bin/env python3
"""Validate G2 case, control, mutant, baseline, and feasibility design artifacts.

The validator never generates or rewrites design data. It returns 0 when the
design is structurally complete even while signed approval is pending, 1 for a
blocking defect, and records G2 completion separately in build/g2-report.json.
"""
import os
import sys

if not sys.flags.isolated:
    os.execv(sys.executable, [sys.executable, '-I', os.path.abspath(__file__), *sys.argv[1:]])
script_dir = os.path.dirname(os.path.abspath(__file__))
if sys.path and os.path.abspath(sys.path[0] or '') == script_dir:
    del sys.path[0]

import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import uuid

import jsonschema
import yaml

ROOT = pathlib.Path(os.environ.get('G2_REPO_ROOT') or pathlib.Path(__file__).resolve().parents[1]).resolve()
BUILD = ROOT / 'build'
APPROVAL_REL = 'tests/approvals/g2.yaml'
APPROVAL_PATH = ROOT / APPROVAL_REL
PROTECTED_PATHS = (
    'tests/cases.yaml',
    'tests/feasibility.yaml',
    'tests/mutants/baselines.yaml',
    'tests/mutants/catalog.yaml',
    'tests/mutants/control-mutants.yaml',
    'tests/fixtures/sut/sp-full-slo-enc/config.yaml',
    'tests/fixtures/sut/sp-core-minimal/config.yaml',
    'tests/fixtures/sut/idp-full/config.yaml',
    'tests/fixtures/sut/idp-core-no-ecp/config.yaml',
    'tests/fixtures/session/fresh-authenticated-session.yaml',
    'tests/approvals/g2.yaml',
    'schema/cases-v1.json',
    'schema/mutants-v1.json',
    'schema/g2-feasibility-v1.json',
    'schema/g2-approval-v1.json',
    'tools/g2_validate.py',
    'tools/g2_trusted_verify.py',
    'tools/g2_ci_verify.sh',
    'tools/requirements.lock',
    '.github/workflows/g2.yml',
    'saml/src/main/java/org/samlier/saml/binding/RedirectSignatureInput.java',
    'saml/src/test/java/org/samlier/saml/G2FeasibilitySpikeTest.java',
)

checks = []

def check(cid, description, passed, detail=''):
    checks.append({
        'id': cid,
        'description': description,
        'result': 'PASS' if passed else 'FAIL',
        'detail': str(detail)[:1000],
    })
    return passed

def load_yaml(relative):
    return yaml.safe_load((ROOT / relative).read_text(encoding='utf-8'))

def load_json(relative):
    return json.loads((ROOT / relative).read_text(encoding='utf-8'))

def sha_bytes(value):
    return 'sha256:' + hashlib.sha256(value).hexdigest()

def sha_file(relative):
    return sha_bytes((ROOT / relative).read_bytes())

def canonical_digest(value):
    encoded = json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
    return sha_bytes(encoded)

def git(*args, binary=False):
    return subprocess.run(
        ['git', '-C', str(ROOT), *args], capture_output=True,
        text=not binary, timeout=30, check=False)

def exact_commit(value):
    value = str(value or '')
    if not re.fullmatch(r'[0-9a-f]{40}', value):
        return False
    result = git('rev-parse', '--verify', '--quiet', value + '^{commit}')
    return result.returncode == 0 and result.stdout.strip() == value

coverage = load_yaml('tests/coverage.yaml')
g1_approval = load_yaml('tests/approvals/g1.yaml')
case_doc = load_yaml('tests/cases.yaml')
baseline_doc = load_yaml('tests/mutants/baselines.yaml')
mutant_doc = load_yaml('tests/mutants/catalog.yaml')
control_doc = load_yaml('tests/mutants/control-mutants.yaml')
feasibility = load_yaml('tests/feasibility.yaml')

schema_errors = []
for relative, document, schema_relative in (
    ('tests/cases.yaml', case_doc, 'schema/cases-v1.json'),
    ('tests/mutants/baselines.yaml', baseline_doc, 'schema/mutants-v1.json'),
    ('tests/mutants/catalog.yaml', mutant_doc, 'schema/mutants-v1.json'),
    ('tests/mutants/control-mutants.yaml', control_doc, 'schema/mutants-v1.json'),
    ('tests/feasibility.yaml', feasibility, 'schema/g2-feasibility-v1.json'),
):
    try:
        jsonschema.Draft202012Validator(load_json(schema_relative)).validate(document)
    except Exception as error:
        schema_errors.append(f'{relative}: {error}')
check('G2-01', 'all G2 artifacts satisfy their JSON Schemas', not schema_errors, schema_errors[:5])

all_obligations = {
    obligation['key']: obligation
    for requirement in coverage['requirements']
    for obligation in requirement['obligations']
}
targets = {key: value for key, value in all_obligations.items() if value['testability'] != 'NOT_OBSERVABLE'}
not_observable = set(all_obligations) - set(targets)
cases = case_doc.get('cases') or []
case_by_id = {case.get('id'): case for case in cases}
mutants = mutant_doc.get('mutants') or []
mutant_by_id = {mutant.get('id'): mutant for mutant in mutants}
baselines = {base['id']: base for base in baseline_doc.get('baselines') or []}
outcomes = baseline_doc.get('outcomes') or {}

check('G2-02', 'the case catalog is bound to the currently approved G1 target commit',
      case_doc.get('catalog_commit') == g1_approval.get('target_commit'),
      f"cases={case_doc.get('catalog_commit')} g1={g1_approval.get('target_commit')}")
case_ids = [case.get('id') for case in cases]
check('G2-03', 'case IDs are unique', len(case_ids) == len(set(case_ids)), len(case_ids))
mutant_ids = [mutant.get('id') for mutant in mutants]
check('G2-04', 'mutant IDs are unique', len(mutant_ids) == len(set(mutant_ids)), len(mutant_ids))

case_obligations = {case.get('obligation') for case in cases}
check('G2-05', 'every observable obligation and no NOT_OBSERVABLE obligation has a case',
      case_obligations == set(targets),
      {'missing': sorted(set(targets) - case_obligations)[:8],
       'extra': sorted(case_obligations - set(targets))[:8],
       'not_observable': sorted(not_observable)})

role_errors = []
for key, obligation in targets.items():
    actual = {case['role'] for case in cases if case.get('obligation') == key}
    if actual != set(obligation['roles']):
        role_errors.append(f'{key}: expected={obligation["roles"]} actual={sorted(actual)}')
check('G2-06', 'every obligation is designed independently for every target role', not role_errors, role_errors[:8])

def expand_variants(key, seen=None):
    seen = set() if seen is None else set(seen)
    if key in seen:
        raise ValueError(f'linked-obligation cycle at {key}')
    seen.add(key)
    owner = all_obligations[key]
    result = {f"{key}#{item['id']}": 'owner_condition' for item in owner.get('required_variants') or []}
    for link in owner.get('linked_obligations') or []:
        linked = all_obligations[link['obligation']]
        selected = set(link.get('variants') or [v['id'] for v in linked.get('required_variants') or []])
        scope = link.get('variant_applicability', 'owner_condition')
        for variant in linked.get('required_variants') or []:
            if variant['id'] in selected:
                result[f"{linked['key']}#{variant['id']}"] = scope
        for reference, inherited_scope in expand_variants(linked['key'], seen).items():
            origin, variant_id = reference.split('#', 1)
            if origin != linked['key'] or variant_id in selected:
                result.setdefault(reference, scope if inherited_scope == 'owner_condition' else inherited_scope)
    return result

variant_errors = []
mode_errors = []
digest_errors = []
control_errors = []
mutant_reference_errors = []
for case in cases:
    key = case.get('obligation')
    obligation = targets.get(key)
    if obligation is None:
        continue
    expected = expand_variants(key)
    if set(case.get('covers_variants') or []) != set(expected):
        variant_errors.append(f"{case['id']}: variant set mismatch")
    if case.get('variant_scopes') != {ref: expected[ref] for ref in sorted(expected)}:
        variant_errors.append(f"{case['id']}: variant scope mismatch")
    if case.get('mode') != obligation['testability']:
        mode_errors.append(f"{case['id']}: mode")
    if obligation['testability'] == 'CONFIG':
        if case.get('configuration_failure_semantics') != obligation.get('configuration_failure_semantics'):
            mode_errors.append(f"{case['id']}: configuration semantics")
    elif 'configuration_failure_semantics' in case:
        mode_errors.append(f"{case['id']}: unexpected configuration semantics")
    kinds = [control.get('kind') for control in case.get('controls') or []]
    if not ({'positive', 'negative'} <= set(kinds) or case.get('control_waiver_en')):
        control_errors.append(case['id'])
    if not case.get('counterexample_en'):
        control_errors.append(case['id'] + ':counterexample')
    positive_fixtures = {control.get('fixture') for control in case.get('controls') or []
                         if control.get('kind') == 'positive'}
    negative_fixtures = {control.get('fixture') for control in case.get('controls') or []
                         if control.get('kind') == 'negative'}
    if positive_fixtures != {case.get('baseline')}:
        control_errors.append(case['id'] + ':positive fixture is not its baseline')
    if negative_fixtures != set(case.get('detected_by_mutants') or []):
        control_errors.append(case['id'] + ':negative fixtures do not match its mutants')
    if not case.get('detected_by_mutants') and not case.get('mutant_waiver'):
        mutant_reference_errors.append(case['id'] + ':no mutant or waiver')
    for mutant_id in case.get('detected_by_mutants') or []:
        if mutant_id not in mutant_by_id:
            mutant_reference_errors.append(case['id'] + ':' + mutant_id)
    canonical = {key2: value for key2, value in case.items() if key2 not in ('case_digest', 'review')}
    if case.get('case_digest') != canonical_digest(canonical):
        digest_errors.append(case['id'])
    if case.get('obligation_digest') != obligation['review']['obligation_digest']:
        digest_errors.append(case['id'] + ':obligation')

check('G2-07', 'covers_variants exactly covers owner and transitive linked variants with runtime scopes',
      not variant_errors, variant_errors[:8])
check('G2-08', 'case mode and configuration semantics match the approved obligation',
      not mode_errors, mode_errors[:8])
check('G2-09', 'every case has positive/negative controls and a counterexample',
      not control_errors, control_errors[:8])
check('G2-10', 'all case and obligation digests match canonical content',
      not digest_errors, digest_errors[:8])
check('G2-11', 'every case references a known mutant or an executable-control waiver',
      not mutant_reference_errors, mutant_reference_errors[:8])

dependency_errors = []
graph = {case['id']: list((case.get('requires') or {}).get('passed_cases') or []) for case in cases}
session_fixture = load_yaml('tests/fixtures/session/fresh-authenticated-session.yaml')
if (session_fixture.get('id') != 'fresh-authenticated-session'
        or (session_fixture.get('on_failure') or {}).get('outcome') != 'not_verified'):
    dependency_errors.append('fresh-authenticated-session: invalid setup or failure outcome')
expected_destructive = {'IIP-SP14.p', 'IIP-SP14.z', 'IIP-IDP17.e', 'IIP-IDP17.q'}
for case_id, dependencies in graph.items():
    for dependency in dependencies:
        if dependency not in case_by_id:
            dependency_errors.append(f'{case_id}: unknown {dependency}')
    case = case_by_id[case_id]
    requirements = case.get('requires') or {}
    if requirements.get('session') == 'required' and requirements.get('setup_fixture') != 'fresh-authenticated-session':
        dependency_errors.append(f'{case_id}: required session lacks the fixed setup fixture')
    if requirements.get('session') != 'required' and requirements.get('setup_fixture'):
        dependency_errors.append(f'{case_id}: session setup fixture is present without a required session')
    if case.get('destroys_session') and requirements.get('session') != 'required':
        dependency_errors.append(f'{case_id}: session-destroying case lacks an isolated session prerequisite')
    is_interactive_slo = (case.get('mode') == 'BROWSER'
                          and re.match(r'^IIP-(?:SP1[4-7]|IDP(?:17|18|19|20))\.', case.get('obligation', '')))
    if is_interactive_slo and requirements.get('session') != 'required':
        dependency_errors.append(f'{case_id}: interactive SLO case lacks a fresh session')
    if bool(case.get('destroys_session')) != (case.get('obligation') in expected_destructive):
        dependency_errors.append(f'{case_id}: session-destruction classification differs from the reviewed set')

visiting = set()
visited = set()
def visit(case_id):
    if case_id in visiting:
        dependency_errors.append(f'cycle at {case_id}')
        return
    if case_id in visited:
        return
    visiting.add(case_id)
    for dependency in graph.get(case_id, []):
        visit(dependency)
    visiting.remove(case_id)
    visited.add(case_id)
for case_id in graph:
    visit(case_id)
check('G2-12', 'case dependencies exist, are acyclic, and order session destruction',
      not dependency_errors, dependency_errors[:8])
check('G2-13', 'all cases are assigned to M1, M2, or M3',
      all(case.get('milestone') in ('M1', 'M2', 'M3') for case in cases))

baseline_errors = []
required_baselines = {'sp-full-slo-enc', 'sp-core-minimal', 'idp-full', 'idp-core-no-ecp'}
if set(baselines) != required_baselines or set(outcomes) != required_baselines:
    baseline_errors.append('baseline matrix IDs differ from the required IdP/SP Core/Full matrix')
for base_id, base in baselines.items():
    vector = outcomes.get(base_id) or {}
    fixture_path = ROOT / base.get('config_fixture', '') / 'config.yaml'
    if not fixture_path.is_file():
        baseline_errors.append(f'{base_id}: config fixture does not exist')
    else:
        fixture = yaml.safe_load(fixture_path.read_text(encoding='utf-8')) or {}
        if fixture.get('role') != base.get('role') or fixture.get('profile') != base.get('profile'):
            baseline_errors.append(f'{base_id}: config fixture role/profile mismatch')
        if fixture.get('features') != base.get('declared_features'):
            baseline_errors.append(f'{base_id}: config fixture features differ from the baseline')
        if fixture.get('conditions') != base.get('condition_results'):
            baseline_errors.append(f'{base_id}: config fixture condition results differ from the baseline')
    if set(vector) != set(targets):
        baseline_errors.append(f'{base_id}: outcome vector is not complete')
        continue
    for key, expectation in vector.items():
        obligation = targets[key]
        if base['role'] not in obligation['roles'] and expectation != {'outcome': 'not_applicable', 'reason_code': 'role_mismatch'}:
            baseline_errors.append(f'{base_id}:{key}: role mismatch is not explicit')
        if expectation.get('outcome') == 'not_applicable' and expectation.get('reason_code') not in ('role_mismatch', 'condition_false'):
            baseline_errors.append(f'{base_id}:{key}: forbidden not_applicable reason')
check('G2-14', 'baseline matrix is complete and uses NOT_APPLICABLE only for role/condition',
      not baseline_errors, baseline_errors[:8])

mutant_errors = []
detected = set()
for mutant in mutants:
    canonical = {key: value for key, value in mutant.items() if key != 'mutant_digest'}
    if mutant.get('mutant_digest') != canonical_digest(canonical):
        mutant_errors.append(mutant.get('id', '<missing>') + ':digest')
    base = mutant.get('base')
    if base not in baselines:
        mutant_errors.append(mutant.get('id', '<missing>') + ':base')
        continue
    changes = mutant.get('expected_changes') or {}
    executor = mutant.get('executor') or {}
    matching_cases = [case for case in cases
                      if case.get('obligation') in changes
                      and case.get('role') == mutant.get('target_role')]
    if len(matching_cases) != 1 or executor.get('trigger_variant') not in matching_cases[0].get('covers_variants', []):
        mutant_errors.append(mutant.get('id', '<missing>') + ':non-executable trigger')
    else:
        trigger_ref = executor.get('trigger_variant')
        trigger_key, trigger_id = trigger_ref.split('#', 1)
        trigger_obligation = all_obligations.get(trigger_key) or {}
        trigger = next((item for item in trigger_obligation.get('required_variants') or []
                        if item.get('id') == trigger_id), None)
        expected_trigger = None if trigger is None else (
            f"{trigger_obligation['summary_en']} — Variant: {trigger['description_en']}")
        if trigger is None or executor.get('trigger_en') != expected_trigger:
            mutant_errors.append(mutant.get('id', '<missing>') + ':trigger text is not bound to G1')
    expected_adapter = str(mutant.get('target_role')) + '-mutant-target'
    if executor.get('adapter') != expected_adapter or executor.get('mutation_point') not in changes:
        mutant_errors.append(mutant.get('id', '<missing>') + ':executor scope')
    for key, expected in changes.items():
        if key not in targets or expected.get('outcome') != 'violated':
            mutant_errors.append(mutant['id'] + ':' + key + ':expected change')
            continue
        if outcomes[base][key].get('outcome') != 'satisfied':
            mutant_errors.append(mutant['id'] + ':' + key + ':baseline is not satisfied')
        obligation = targets[key]
        operation = executor.get('operation')
        if obligation['testability'] == 'CONFIG':
            semantics = obligation.get('configuration_failure_semantics')
            if semantics == 'test_precondition' and operation == 'remove_required_capability':
                mutant_errors.append(mutant['id'] + ':' + key + ':test precondition cannot be mutated into a target violation')
            if (semantics == 'normative_capability' and obligation['level'] not in ('MUST_NOT', 'SHOULD_NOT')
                    and operation != 'remove_required_capability'):
                mutant_errors.append(mutant['id'] + ':' + key + ':normative capability mutation is missing')
        if (obligation['testability'] == 'ATTESTED'
                and operation not in ('emit_nonconforming_evidence', 'emit_prohibited_behavior')):
            mutant_errors.append(mutant['id'] + ':' + key + ':attested mutation lacks nonconforming evidence')
        detected.add(key)
    if mutant.get('unchanged_required') != 'all_others':
        mutant_errors.append(mutant['id'] + ':unchanged rule')
check('G2-15', 'mutants have a satisfied baseline, violated expected change, and all-others oracle',
      not mutant_errors, mutant_errors[:8])

waived = {case['obligation'] for case in cases if case.get('mutant_waiver')}
check('G2-16', 'every observable obligation is detected by a mutant or has an alternative executable fixture',
      detected | waived == set(targets), sorted(set(targets) - detected - waived)[:8])

control_mutants = {item['id']: item for item in control_doc.get('control_mutants') or []}
check('G2-17', 'accept-everything and reject-everything validate negative and positive controls',
      set(control_mutants) == {'accept-everything', 'reject-everything'}
      and control_mutants['reject-everything']['must_change_cases'] == 'all_with_positive_control'
      and control_mutants['accept-everything']['must_change_cases'] == 'all_with_negative_control')

spikes = {spike['id']: spike for spike in feasibility.get('spikes') or []}
source = (ROOT / 'saml/src/test/java/org/samlier/saml/G2FeasibilitySpikeTest.java').read_text(encoding='utf-8')
spike_errors = []
for spike_id in [f'S{number}' for number in range(1, 7)]:
    spike = spikes.get(spike_id)
    if not spike or spike.get('status') != 'PASS':
        spike_errors.append(spike_id + ':status')
        continue
    for verifier in spike.get('verified_by') or []:
        method = verifier.rsplit('.', 1)[-1]
        if method not in source:
            spike_errors.append(spike_id + ':' + verifier)
check('G2-18', 'all six feasibility spikes are backed by named executable tests',
      not spike_errors and set(spikes) == {f'S{number}' for number in range(1, 7)}, spike_errors)

forbidden_keys = []
def scan_keys(value, path=''):
    if isinstance(value, dict):
        for key, child in value.items():
            if key.lower() in ('level', 'verdict'):
                forbidden_keys.append(path + '/' + key)
            scan_keys(child, path + '/' + str(key))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            scan_keys(child, path + f'/{index}')
scan_keys(case_doc)
scan_keys(mutant_doc)
check('G2-19', 'G2 definitions contain outcomes but no case-side level or verdict',
      not forbidden_keys, forbidden_keys[:8])

# Signed G2 approval. Absence is a pending gate, not an authoring defect.
approval = None
approval_errors = []
approved_cases = set()
signature_info = None
if APPROVAL_PATH.exists():
    log = git('log', '-1', '--format=%H', '--', APPROVAL_REL)
    approval_commit = log.stdout.strip() if log.returncode == 0 else ''
    if not approval_commit:
        approval_errors.append('approval record is not committed')
    else:
        verify = git('verify-commit', approval_commit)
        if verify.returncode != 0:
            approval_errors.append('approval commit signature verification failed')
        blob = git('show', f'{approval_commit}:{APPROVAL_REL}', binary=True)
        if blob.returncode != 0:
            approval_errors.append('approval record cannot be read from signed commit')
        else:
            approval = yaml.safe_load(blob.stdout.decode())
            approval['_signed_commit'] = approval_commit
            approval_for_schema = {key: value for key, value in approval.items() if key != '_signed_commit'}
            try:
                jsonschema.Draft202012Validator(load_json('schema/g2-approval-v1.json')).validate(approval_for_schema)
            except Exception as error:
                approval_errors.append(f'approval schema: {error}')
            target_commit = approval.get('target_commit')
            if not exact_commit(target_commit):
                approval_errors.append('target_commit is not an exact 40-character commit SHA')
            elif git('merge-base', '--is-ancestor', target_commit, approval_commit).returncode != 0:
                approval_errors.append('approval commit does not descend from target commit')
            else:
                changed = sorted(git('diff', '--name-only', f'{target_commit}..{approval_commit}').stdout.split())
                if changed != [APPROVAL_REL]:
                    approval_errors.append(f'C..A changes are not limited to {APPROVAL_REL}: {changed[:6]}')
            for relative in PROTECTED_PATHS:
                current = ROOT / relative
                signed = git('show', f'{approval_commit}:{relative}', binary=True)
                if not current.exists() or signed.returncode != 0 or sha_bytes(current.read_bytes()) != sha_bytes(signed.stdout):
                    approval_errors.append(relative + ' differs from signed approval commit')
            for relative, wanted in (approval.get('artifact_digests') or {}).items():
                target_blob = git('show', f"{approval.get('target_commit')}:{relative}", binary=True)
                if target_blob.returncode != 0 or sha_bytes(target_blob.stdout) != wanted:
                    approval_errors.append(relative + ': target artifact digest mismatch')
            expected_artifacts = set(PROTECTED_PATHS) - {APPROVAL_REL}
            actual_artifacts = set((approval.get('artifact_digests') or {}).keys())
            if actual_artifacts != expected_artifacts:
                approval_errors.append(
                    'artifact_digests must cover exactly every protected target artifact: '
                    f'missing={sorted(expected_artifacts - actual_artifacts)[:6]} '
                    f'extra={sorted(actual_artifacts - expected_artifacts)[:6]}')
            entries = approval.get('approvals') or []
            entry_map = {entry.get('case'): entry for entry in entries}
            if len(entry_map) != len(entries):
                approval_errors.append('approval case entries are not unique')
            for case_id, case in case_by_id.items():
                entry = entry_map.get(case_id)
                if not entry or entry.get('case_digest') != case.get('case_digest'):
                    approval_errors.append(case_id + ': missing or stale approval')
                    continue
                if entry.get('reviewer') == case.get('authored_by'):
                    approval_errors.append(case_id + ': reviewer equals author')
                try:
                    parsed = datetime.datetime.fromisoformat(str(entry.get('approved_at')).replace('Z', '+00:00'))
                    if parsed.tzinfo is None:
                        raise ValueError()
                except Exception:
                    approval_errors.append(case_id + ': approved_at lacks a timezone')
                approved_cases.add(case_id)
            signer = git('log', '-1', '--format=%GS|%GK|%GT', approval_commit)
            if signer.returncode == 0:
                parts = signer.stdout.strip().split('|')
                signature_info = {'signer': parts[0] if parts else None,
                                  'key': parts[1] if len(parts) > 1 else None,
                                  'trust': parts[2] if len(parts) > 2 else None}
                declared_reviewers = {entry.get('reviewer') for entry in entries}
                evidence_reviewers = set((approval.get('evidence') or {}).get('reviewers') or [])
                if evidence_reviewers != declared_reviewers:
                    approval_errors.append('evidence reviewers differ from per-case reviewers')
                if parts and declared_reviewers - {parts[0]}:
                    approval_errors.append('approval reviewer does not match the signing principal')

check('G2-30', 'signed approval record is valid when present', not approval_errors, approval_errors[:8])
pending = set(case_by_id) - approved_cases
check('G2-31', 'all role-specific case designs are independently approved', not pending, f'{len(pending)}/{len(case_by_id)} unapproved')

pending_ids = {'G2-31'}
blocking = [item for item in checks if item['result'] == 'FAIL' and item['id'] not in pending_ids]
complete = not blocking and not pending
report = {
    'task': 'g2Check',
    'run_id': str(uuid.uuid4()),
    'executed_at': datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
    'provenance': {
        'repo_root': str(ROOT),
        'validator_source': os.environ.get('G2_VALIDATOR_SOURCE') or 'working-tree',
        'validator_source_kind': os.environ.get('G2_VALIDATOR_SOURCE_KIND') or 'working-tree',
        'runner_source': os.environ.get('G2_RUNNER_SOURCE') or 'working-tree',
    },
    'totals': {
        'requirements': len(coverage['requirements']),
        'obligations': len(all_obligations),
        'case_target_obligations': len(targets),
        'role_cases': len(cases),
        'mutants': len(mutants),
        'baselines': len(baselines),
        'feasibility_spikes': len(spikes),
        'checks': len(checks),
        'passed': sum(item['result'] == 'PASS' for item in checks),
        'failed': sum(item['result'] == 'FAIL' for item in checks),
        'blocking_failures': len(blocking),
    },
    'g2_approval': None if approval is None else {
        'target_commit': approval.get('target_commit'),
        'approval_commit': approval.get('_signed_commit'),
        'signature': signature_info,
        'approved_cases': len(approved_cases),
        'artifact_digests': approval.get('artifact_digests'),
    },
    'g2': {
        'state': 'APPROVED' if complete else 'PENDING_REVIEW',
        'unapproved': len(pending),
        'blocking_failures': [item['id'] for item in blocking],
        'complete': complete,
        'complete_formula': 'no blocking failures AND every role-specific case approved by a signed record',
    },
    'checks': checks,
}
BUILD.mkdir(exist_ok=True)
(BUILD / 'g2-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"{report['totals']['passed']}/{report['totals']['checks']} PASS  (blocking failures: {len(blocking)})")
for item in checks:
    if item['result'] == 'FAIL':
        prefix = 'g2-pending' if item['id'] in pending_ids else 'BLOCK'
        print(f"  {prefix} {item['id']} {item['description']} | {item['detail'][:160]}")
sys.exit(1 if blocking else 0)
