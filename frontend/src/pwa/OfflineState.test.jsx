import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from '@/test/axe'
import { OfflineState } from './OfflineState'

describe('OfflineState', () => {
  it('explainsTheMissingConnectionAndRetries', async () => {
    const onRetry = vi.fn()
    const { container } = render(<OfflineState onRetry={onRetry} />)
    expect(screen.getByText('Sin conexión')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetry).toHaveBeenCalledOnce()
    expect(await axe(container)).toHaveNoViolations()
  })
})
