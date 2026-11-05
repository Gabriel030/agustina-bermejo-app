import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from '@/test/axe'
import { useUnsavedChangesGuard } from '@/features/academic-structure/components/UnsavedChangesGuard'
import { SystemBar } from './SystemBar'
import { registerServiceWorker, resetServiceWorkerState } from './registerServiceWorker'

function setOnLine(value) {
  vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(value)
  act(() => {
    window.dispatchEvent(new Event(value ? 'online' : 'offline'))
  })
}

function emitter() {
  const handlers = {}
  return { handlers, addEventListener: (name, handler) => (handlers[name] = handler) }
}

async function publishUpdate() {
  const waiting = { postMessage: vi.fn() }
  const registration = { ...emitter(), waiting, installing: null }
  const serviceWorker = { ...emitter(), controller: {}, register: vi.fn().mockResolvedValue(registration) }
  await act(async () => {
    await registerServiceWorker({ serviceWorker, reload: vi.fn() })
  })
  return waiting
}

function DirtyForm() {
  useUnsavedChangesGuard(true)
  return <SystemBar />
}

afterEach(() => {
  vi.restoreAllMocks()
  resetServiceWorkerState()
})

describe('SystemBar', () => {
  it('keepsAnEmptyPoliteStatusRegionInTheDomWhileOnline', () => {
    setOnLine(true)
    render(<SystemBar />)
    const region = screen.getByRole('status')
    expect(region).toBeEmptyDOMElement()
    expect(region).toHaveAttribute('aria-live', 'polite')
  })

  it('showsTheOfflineMessageOnlyWhileOffline', () => {
    setOnLine(true)
    render(<SystemBar />)
    expect(screen.queryByText('Sin conexión')).not.toBeInTheDocument()
    setOnLine(false)
    expect(screen.getByText('Sin conexión')).toBeInTheDocument()
    setOnLine(true)
    expect(screen.queryByText('Sin conexión')).not.toBeInTheDocument()
  })

  it('offersTheUpdateAndAppliesItOnClick', async () => {
    setOnLine(true)
    const user = userEvent.setup()
    render(<SystemBar />)
    expect(screen.queryByRole('button', { name: 'Actualizar' })).not.toBeInTheDocument()
    const waiting = await publishUpdate()
    await user.click(screen.getByRole('button', { name: 'Actualizar' }))
    expect(waiting.postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' })
  })

  it('givesPriorityToOfflineOverTheNewVersion', async () => {
    setOnLine(true)
    render(<SystemBar />)
    await publishUpdate()
    expect(screen.getByText('Hay una versión nueva')).toBeInTheDocument()
    setOnLine(false)
    expect(screen.getByText('Sin conexión')).toBeInTheDocument()
    expect(screen.queryByText('Hay una versión nueva')).not.toBeInTheDocument()
  })

  it('leavesTheBeforeUnloadPromptActiveWhenAFormHasUnsavedChanges', async () => {
    setOnLine(true)
    render(
      <MemoryRouter>
        <DirtyForm />
      </MemoryRouter>,
    )
    await publishUpdate()
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('hasNoAccessibilityViolationsOfflineAndWithUpdate', async () => {
    setOnLine(false)
    const { container } = render(<SystemBar />)
    expect(await axe(container)).toHaveNoViolations()
    setOnLine(true)
    await publishUpdate()
    expect(await axe(container)).toHaveNoViolations()
  })
})
