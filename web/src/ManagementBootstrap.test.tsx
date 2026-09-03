import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { ManagementBootstrap } from './ManagementBootstrap'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  window.history.replaceState(null, '', '/')
  window.sessionStorage.clear()
})

test('removes the fragment before exchanging it and keeps only the CSRF token in session storage', async () => {
  const runId = 'run_0123456789ABCDEFGHJKMNPQRS'
  const token = 'a'.repeat(43)
  window.history.replaceState(null, '', `/manage/${runId}#t=${token}`)
  const replace = vi.spyOn(window.history, 'replaceState')
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url.includes('/interactions')) return new Response(JSON.stringify([]), {
      status: 200, headers: { 'content-type': 'application/json' },
    })
    if (url.includes('/bootstrap-contracts')) return json([])
    if (url.includes('/metadata-lab')) return json({
      runId, planId: 'plan', selectedVariant: 'control', metadataUrl: 'https://suite.example/metadata',
      availableVariants: ['control'], ingestionMode: 'MANUAL_REFRESH', campaignVariants: [],
      campaignIndex: 0, campaignComplete: false, pollingDelaySeconds: 15, operatorContinuationActions: 0, automaticStartUrl: null, automaticContinueUrl: null, preloadedMetadataUrl: null, preloadedDownloadUrl: null,
      preloadedStartUrl: null, preloadedVariants: [], preloadedFetched: false,
    })
    if (url.includes('/protocol-evidence')) return json({ eligibleCases: 0, readyCases: 0, cases: [] })
    if (url.includes('/active-probe')) return json({ state: 'NOT_STARTED' })
    expect(window.location.hash).toBe('')
    expect(init?.body).toBe(JSON.stringify({ runId, token }))
    return new Response(JSON.stringify({ runId, csrfToken: 'c'.repeat(43) }), {
      status: 200, headers: { 'content-type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)

  render(<ManagementBootstrap runId={runId} />)

  expect(await screen.findByText('Run unlocked')).toBeTruthy()
  expect(replace).toHaveBeenCalledWith(null, '', `/manage/${runId}`)
  expect(window.sessionStorage.getItem(`samlscope.csrf.${runId}`)).toBe('c'.repeat(43))
  expect(document.body.textContent).not.toContain(token)
})

test('opens a self-hosted management page without a fragment secret', async () => {
  const runId = 'run_0123456789ABCDEFGHJKMNPQRS'
  window.history.replaceState(null, '', `/manage/${runId}`)
  vi.stubGlobal('fetch', vi.fn(async (url: string) => new Response(JSON.stringify(
    url === '/api/health' ? { status: 'ok', version: 'test', mode: 'selfhosted' }
      : url.includes('/metadata-lab') ? {
        runId, planId: 'plan', selectedVariant: 'control', metadataUrl: 'https://suite.example/metadata',
        availableVariants: ['control'], ingestionMode: 'MANUAL_REFRESH', campaignVariants: [],
        campaignIndex: 0, campaignComplete: false, pollingDelaySeconds: 15, operatorContinuationActions: 0, automaticStartUrl: null, automaticContinueUrl: null, preloadedMetadataUrl: null, preloadedDownloadUrl: null,
        preloadedStartUrl: null, preloadedVariants: [], preloadedFetched: false,
      } : url.includes('/protocol-evidence') ? { eligibleCases: 0, readyCases: 0, cases: [] } : [],
  ), { status: 200, headers: { 'content-type': 'application/json' } })))

  render(<ManagementBootstrap runId={runId} />)

  expect(await screen.findByText('Run unlocked')).toBeTruthy()
  expect(await screen.findByText('No pending interactions.')).toBeTruthy()
})

function json(value: unknown) {
  return new Response(JSON.stringify(value), { status: 200, headers: { 'content-type': 'application/json' } })
}
