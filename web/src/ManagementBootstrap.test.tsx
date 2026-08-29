import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { ManagementBootstrap } from './ManagementBootstrap'

afterEach(() => {
  vi.restoreAllMocks()
  window.history.replaceState(null, '', '/')
  window.sessionStorage.clear()
})

test('removes the fragment before exchanging it and keeps only the CSRF token in session storage', async () => {
  const runId = 'run_0123456789ABCDEFGHJKMNPQRS'
  const token = 'a'.repeat(43)
  window.history.replaceState(null, '', `/manage/${runId}#t=${token}`)
  const replace = vi.spyOn(window.history, 'replaceState')
  const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
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
  expect(window.sessionStorage.getItem(`samlier.csrf.${runId}`)).toBe('c'.repeat(43))
  expect(document.body.textContent).not.toContain(token)
})
