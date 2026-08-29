import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => vi.restoreAllMocks())

test('separates operational checks from conformance results', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([]), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })))
  render(<App />)
  expect(screen.getByText(/Operational quick checks remain separate from conformance results/)).toBeTruthy()
})
