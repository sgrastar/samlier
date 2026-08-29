import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => vi.restoreAllMocks())

test('separates operational checks from conformance results', async () => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => new Response(JSON.stringify(
    String(input).endsWith('/api/health')
      ? { status: 'ok', version: '0.1.0', mode: 'selfhosted' }
      : [],
  ), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })))
  render(<App />)
  expect(screen.getByText(/Operational quick checks remain separate from conformance results/)).toBeTruthy()
})
