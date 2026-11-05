import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from '@/test/axe'
import { InstallPrompt } from './InstallPrompt'
import { captureInstallPrompt, resetInstallPrompt } from './installPromptStore'

let stopCapturing

function fireInstallEvent(outcome = 'accepted') {
  const event = new Event('beforeinstallprompt', { cancelable: true })
  event.prompt = vi.fn().mockResolvedValue(undefined)
  event.userChoice = Promise.resolve({ outcome })
  act(() => {
    window.dispatchEvent(event)
  })
  return event
}

function setup() {
  stopCapturing = captureInstallPrompt()
}

afterEach(() => {
  stopCapturing?.()
  resetInstallPrompt()
  vi.restoreAllMocks()
})

describe('InstallPrompt', () => {
  it('rendersNothingUntilTheBrowserOffersInstallation', () => {
    setup()
    const { container } = render(<InstallPrompt />)
    expect(container).toBeEmptyDOMElement()
  })

  it('opensTheNativePromptOnlyAfterTheClickAndHidesAfterwards', async () => {
    setup()
    render(<InstallPrompt />)
    const event = fireInstallEvent()
    expect(event.defaultPrevented).toBe(true)
    expect(event.prompt).not.toHaveBeenCalled()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Instalar' }))
    expect(event.prompt).toHaveBeenCalledOnce()
    expect(screen.queryByRole('button', { name: 'Instalar' })).not.toBeInTheDocument()
  })

  it('hidesTheInvitationWhenTheUserDeclines', async () => {
    setup()
    render(<InstallPrompt />)
    fireInstallEvent()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Ahora no' }))
    expect(screen.queryByText('Instalá la app')).not.toBeInTheDocument()
  })

  it('showsShareInstructionsOnIphoneWithoutAnInstallButton', () => {
    setup()
    vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')
    render(<InstallPrompt />)
    expect(screen.getByText(/Agregar a inicio/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Instalar' })).not.toBeInTheDocument()
  })

  it('staysHiddenWhenTheAppRunsInstalled', () => {
    setup()
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    render(<InstallPrompt />)
    fireInstallEvent()
    expect(screen.queryByText('Instalá la app')).not.toBeInTheDocument()
    vi.unstubAllGlobals()
  })

  it('hasNoAccessibilityViolations', async () => {
    setup()
    const { container } = render(<InstallPrompt />)
    fireInstallEvent()
    expect(await axe(container)).toHaveNoViolations()
  })
})
