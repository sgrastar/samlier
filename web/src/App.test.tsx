import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { App } from './App'

afterEach(() => vi.restoreAllMocks())

test('states clearly that M0 produces no conformance verdict', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([]), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })))
  render(<App />)
  expect(screen.getByText(/No conformance verdicts are produced yet/)).toBeTruthy()
})
