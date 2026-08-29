import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { RunManagement } from './RunManagement'

afterEach(() => vi.restoreAllMocks())

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
    return new Response(JSON.stringify(pending ? [{
      caseId: 'IIP-G02-c-idp-01', kind: 'ATTESTATION', promptKey: 'case.g02',
      promptEn: 'Compare the complete value through the approved readback path.', startUrl: null,
      expiresAt: '2026-09-05T00:00:00Z', answerValues: ['satisfied', 'violated', 'unable_to_verify'],
    }] : []), { status: 200, headers: { 'content-type': 'application/json' } })
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
