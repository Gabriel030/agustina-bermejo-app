import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorFallback } from './ErrorFallback'

afterEach(() => vi.restoreAllMocks())

describe('ErrorFallback offline', () => {
  it('showsTheNoConnectionPageWhileOfflineAndRetriesWithReset', async () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false)
    const reset = vi.fn()
    render(<ErrorFallback reset={reset} />)
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(screen.getByText('Sin conexión')).toBeInTheDocument()
    expect(screen.queryByText('Algo salió mal')).not.toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(reset).toHaveBeenCalledOnce()
  })

  it('keepsTheGenericErrorWhileOnline', () => {
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true)
    render(<ErrorFallback reset={() => {}} />)
    expect(screen.getByText('Algo salió mal')).toBeInTheDocument()
  })
})
