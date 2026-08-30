import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { RunManagement } from './RunManagement'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

test('shows approved instructions and submits only an option plus evidence note', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  let pending = true
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    if (init?.method === 'POST') {
      pending = false
      return new Response(JSON.stringify({ status: 'FINISHED' }), {
        status: 200, headers: { 'content-type': 'application/json' },
      })
    }
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/interactions')) return json(pending ? [{
      caseId: 'IIP-G02-c-idp-01', kind: 'ATTESTATION', promptKey: 'case.g02',
      promptEn: 'Compare the complete value through the approved readback path.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z', answerValues: ['satisfied', 'violated', 'unable_to_verify'],
    }] : [])
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement runId="run_0123456789ABCDEFGHJKMNPQRS" csrfToken="csrf" />)
  expect(await screen.findByText(/approved readback path/)).toBeTruthy()
  fireEvent.click(screen.getByLabelText('Satisfied'))
  fireEvent.change(screen.getByLabelText('Evidence note'), { target: { value: 'Admin audit log entry 17' } })
  fireEvent.click(screen.getByText('Record attestation'))

  await waitFor(() => expect(screen.getByText('No pending interactions.')).toBeTruthy())
  const post = calls.find(call => call.init?.method === 'POST')!
  expect(post.init?.body).toBe(JSON.stringify({ value: 'satisfied', note: 'Admin audit log entry 17' }))
  expect(post.init?.headers).toEqual({ 'content-type': 'application/json', 'X-CSRF-Token': 'csrf' })
  expect(String(post.init?.body)).not.toContain('PASS')
})

test('keeps configuration status separate from the later evidence conclusion', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  let stage = 'config'
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    if (init?.method === 'POST') {
      stage = 'evidence'
      return new Response(JSON.stringify({ status: 'WAITING_ATTESTATION' }), {
        status: 200, headers: { 'content-type': 'application/json' },
      })
    }
    if (url.includes('/bootstrap-contracts')) return json(stage === 'config' ? [{
      id: 'authentication-policy', title: 'Authentication and identifier policy', description: 'Shared setup.',
      kind: 'OPERATOR_POLICY', readiness: 'SETUP_REQUIRED', setupUrl: null,
      setupInstruction: 'Prepare this policy once.', pendingCases: 1, caseIds: ['IIP-SSO01-u-sp-01'],
    }] : [])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/interactions')) return json(stage === 'config' ? [{
      caseId: 'IIP-SSO01-u-sp-01', kind: 'CONFIGURATION', promptKey: 'case.config',
      promptEn: 'Activate the approved target configuration.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z',
      answerValues: ['confirmed', 'capability_absent', 'target_config_unavailable', 'capability_undetermined'],
    }] : [{
      caseId: 'IIP-SSO01-u-sp-01', kind: 'ATTESTATION', promptKey: 'case.evidence',
      promptEn: 'Execute both approved controls.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z',
      answerValues: ['evidence_satisfies', 'evidence_violates', 'unable_to_verify'],
    }])
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement
    runId="run_0123456789ABCDEFGHJKMNPQRS"
    focusCaseId="IIP-SSO01-u-sp-01"
    csrfToken="csrf"
  />)
  expect(await screen.findByText(/approved target configuration/)).toBeTruthy()
  fireEvent.click(screen.getByLabelText('Confirmed'))
  fireEvent.click(screen.getByText('Continue case'))

  expect(await screen.findByText(/both approved controls/)).toBeTruthy()
  const post = calls.find(call => call.init?.method === 'POST')!
  expect(post.url).toContain('/configure')
  expect(post.init?.body).toBe(JSON.stringify({ value: 'confirmed', note: '' }))
})

test('a focused browser URL shows only the requested case', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/interactions')) return json([
      {
        caseId: 'IIP-ALG01-a-idp-01', kind: 'BROWSER', promptKey: null,
        promptEn: 'Inspect the target DigestMethod.', startUrl: 'https://suite.example/browser/one',
        expiresAt: '2026-09-05T00:00:00Z', answerValues: ['completed'],
      },
      {
        caseId: 'IIP-ALG02-a-idp-01', kind: 'BROWSER', promptKey: null,
        promptEn: 'Inspect the target SignatureMethod.', startUrl: 'https://suite.example/browser/two',
        expiresAt: '2026-09-05T00:00:00Z', answerValues: ['completed'],
      },
    ])
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement
    runId="run_0123456789ABCDEFGHJKMNPQRS"
    focusCaseId="IIP-ALG01-a-idp-01"
  />)

  expect(await screen.findByText(/target DigestMethod/)).toBeTruthy()
  expect(screen.queryByText(/target SignatureMethod/)).toBeNull()
  expect(screen.getByRole('link', { name: 'Back to Run management' }).getAttribute('href'))
    .toBe('/manage/run_0123456789ABCDEFGHJKMNPQRS')
})

test('evaluates only server-reported ready protocol evidence from the shared metadata contract', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  let evaluated = false
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    if (url.includes('/protocol-evidence/evaluate')) {
      evaluated = true
      return json({
        completed: [{ caseId: 'IIP-MD05-an-idp-01', outcome: 'SATISFIED' }],
        remaining: { eligibleCases: 0, readyCases: 0, cases: [] },
      })
    }
    if (url.includes('/bootstrap-contracts')) return json([{
      id: 'metadata-feed', title: 'Suite-controlled metadata feed', description: 'Shared feed.',
      kind: 'STANDARD_METADATA', readiness: 'FETCH_OBSERVED', setupUrl: metadataLab().metadataUrl,
      setupInstruction: 'Use the stable URL.', pendingCases: evaluated ? 0 : 1,
      caseIds: evaluated ? [] : ['IIP-MD05-an-idp-01'],
    }])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(evaluated ? protocolEvidence() : {
      eligibleCases: 1, readyCases: 1, cases: [{
        caseId: 'IIP-MD05-an-idp-01', ready: true,
        requiredObservations: ['fetched:control', 'used:control'],
        completedObservations: ['fetched:control', 'used:control'], details: {},
      }],
    })
    if (url.includes('/interactions')) return json(evaluated ? [] : [{
      caseId: 'IIP-MD05-an-idp-01', kind: 'CONFIGURATION', promptKey: 'metadata.probe',
      promptEn: 'Use the shared metadata feed.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z', answerValues: ['target_config_unavailable'],
      completionMode: 'TRANSCRIPT_OR_OPERATOR',
    }])
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement runId="run_0123456789ABCDEFGHJKMNPQRS" csrfToken="csrf" />)
  const button = await screen.findByRole('button', {
    name: 'Re-evaluate recorded evidence',
  })
  expect(button.hasAttribute('disabled')).toBe(false)
  fireEvent.click(button)

  expect(await screen.findByText(/IIP-MD05-an-idp-01: SATISFIED/)).toBeTruthy()
  const post = calls.find(call => call.url.includes('/protocol-evidence/evaluate'))!
  expect(post.init?.method).toBe('POST')
  expect(post.init?.headers).toEqual({ 'content-type': 'application/json', 'X-CSRF-Token': 'csrf' })
})

test('explains that protocol-driven configuration answers are only an unavailability fallback', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.includes('/active-probe')) return json({ state: 'NOT_STARTED' })
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url.includes('/interactions')) return json([{
      caseId: 'IIP-MD05-an-idp-01', kind: 'CONFIGURATION', promptKey: 'metadata.probe',
      promptEn: 'Use the shared metadata feed.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z', answerValues: ['target_config_unavailable'],
      completionMode: 'TRANSCRIPT_OR_OPERATOR',
    }])
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement
    runId="run_0123456789ABCDEFGHJKMNPQRS"
    focusCaseId="IIP-MD05-an-idp-01"
  />)

  expect(await screen.findByText(/completes automatically when all required correlated Transcript evidence/))
    .toBeTruthy()
  expect(screen.getByText('Target config unavailable')).toBeTruthy()
})

test('does not offer completed for a transcript-driven browser case', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.includes('/active-probe')) return json({ state: 'NOT_STARTED' })
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url.includes('/interactions')) return json([{
      caseId: 'IIP-SSO03-a-idp-01', kind: 'BROWSER', promptKey: null,
      promptEn: 'Perform the ordinary SSO flow.', startUrl: 'https://suite.example/start',
      expiresAt: '2026-09-05T00:00:00Z', answerValues: [], completionMode: 'TRANSCRIPT',
    }])
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([])
    return json([])
  }))

  render(<RunManagement runId="run_0123456789ABCDEFGHJKMNPQRS" />)

  expect(await screen.findByText(/No completion answer is needed/)).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Browser steps completed' })).toBeNull()
})

test('offers the server-generated active probe launch URL and explains fresh-session isolation', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url.includes('/active-probe')) return json({
      planId: 'plan', state: 'READY', actionId: 'action_probe',
      startUrl: 'https://peer.example/p/plan/probe/action_probe?run=run_0123456789ABCDEFGHJKMNPQRS',
      requiresFreshSession: true, outcome: null,
    })
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json(metadataLab())
    if (url.includes('/protocol-evidence')) return json(protocolEvidence())
    if (url.includes('/interactions')) return json([])
    if (url === '/api/health') return json({ status: 'ok', version: 'test', mode: 'selfhosted' })
    if (url.includes('/api/runs/')) return json({ id: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan' })
    if (url === '/api/plans') return json([{
      plan: { id: 'plan', name: 'Target IdP', profile: 'IDP_CORE', target: { kind: 'IDP', entityId: 'https://idp.example' } },
      entityId: 'https://suite.example/p/plan', metadataUrl: 'https://suite.example/p/plan/metadata',
      mdqUrl: 'https://suite.example/mdq/plan', secondaryIdpEntityId: 'https://suite.example/p/plan/idp/secondary',
      secondaryIdpMetadataUrl: 'https://suite.example/p/plan/idp/secondary/metadata',
    }])
    return json([])
  }))

  render(<RunManagement runId="run_0123456789ABCDEFGHJKMNPQRS" />)

  expect(await screen.findByText('Active error-response probes')).toBeTruthy()
  expect(screen.getByText(/private browser context/)).toBeTruthy()
  expect(screen.getByRole('link', { name: 'Open active probes' }).getAttribute('href'))
    .toContain('/probe/action_probe')
})

function metadataLab() {
  return {
    runId: 'run_0123456789ABCDEFGHJKMNPQRS', planId: 'plan', selectedVariant: 'control',
    metadataUrl: 'https://suite.example/p/plan/metadata/live?run=run_0123456789ABCDEFGHJKMNPQRS',
    availableVariants: ['control', 'no-key-info'],
  }
}

function protocolEvidence() {
  return { eligibleCases: 0, readyCases: 0, cases: [] }
}

function json(value: unknown) {
  return new Response(JSON.stringify(value), { status: 200, headers: { 'content-type': 'application/json' } })
}
