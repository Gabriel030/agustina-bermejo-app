import { useState } from 'react'
import { GlassCard } from '@/components/GlassCard'
import { Button } from '@/components/ui/button'
import { isIos, isStandalone, promptInstall, useCanPromptInstall } from './installPromptStore'

export function InstallPrompt() {
  const canPrompt = useCanPromptInstall()
  const [declined, setDeclined] = useState(false)
  const ios = isIos()

  if (isStandalone() || declined || (!canPrompt && !ios)) return null

  async function install() {
    const outcome = await promptInstall()
    if (outcome === 'dismissed') setDeclined(true)
  }

  return (
    <GlassCard className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      <img src="/icons/icon-192.png" alt="" width="44" height="44" className="size-11 rounded-xl" />
      <div className="flex-1">
        <div className="font-heading text-[15px] font-semibold text-ink">Instalá la app</div>
        <p className="text-sm text-ink-soft">
          {canPrompt
            ? 'Entrá más rápido desde tu pantalla de inicio.'
            : 'Tocá Compartir y elegí "Agregar a inicio". Hace falta para recibir avisos en el iPhone.'}
        </p>
      </div>
      {canPrompt && (
        <div className="flex justify-end gap-2">
          <Button variant="ghost" size="lg" onClick={() => setDeclined(true)}>
            Ahora no
          </Button>
          <Button size="lg" onClick={install}>
            Instalar
          </Button>
        </div>
      )}
    </GlassCard>
  )
}
