import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { StaleStamp } from './StaleStamp'
import { OfflineAwareButton } from './OfflineAwareButton'
import { RequiresConnection } from './RequiresConnection'
import { FIXED_NOW, installFixedClock } from '@/test/fixedClock'

installFixedClock()

const NOW = new Date(FIXED_NOW).getTime()

function setOnline(value) {
  vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(value)
}

afterEach(() => vi.restoreAllMocks())

describe('offline components', () => {
  it('staleStampShowsRelativeAgeAndFetchingState', () => {
    setOnline(false)
    const { rerender } = render(<StaleStamp dataUpdatedAt={NOW - 3 * 3_600_000} />)
    expect(screen.getByText('Actualizado hace 3 h')).toBeInTheDocument()

    setOnline(true)
    rerender(<StaleStamp dataUpdatedAt={NOW - 3 * 3_600_000} isFetching />)
    expect(screen.getByRole('status')).toHaveTextContent('Actualizando…')

    rerender(<StaleStamp dataUpdatedAt={NOW - 5_000} isFetching />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('staleStampIsHiddenWithoutData', () => {
    setOnline(false)
    const { container } = render(<StaleStamp dataUpdatedAt={0} hasData={false} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('mutationButtonsAreDisabledOffline', () => {
    setOnline(true)
    render(<OfflineAwareButton>Justificar</OfflineAwareButton>)
    expect(screen.getByRole('button', { name: 'Justificar' })).toBeEnabled()
    act(() => {
      setOnline(false)
      window.dispatchEvent(new Event('offline'))
    })
    const button = screen.getByRole('button', { name: 'Justificar' })
    expect(button).toBeDisabled()
    expect(button).toHaveAccessibleDescription(/Necesita conexión/)
  })

  it('billingScreenShowsNeedsConnectionOffline', () => {
    setOnline(false)
    render(<RequiresConnection><div>Saldo 1000</div></RequiresConnection>)
    expect(screen.getByText('Necesita conexión')).toBeInTheDocument()
    expect(screen.queryByText('Saldo 1000')).not.toBeInTheDocument()
  })
})
