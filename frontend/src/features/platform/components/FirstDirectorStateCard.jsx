import { Link } from 'react-router-dom';
import { AlertTriangle, ClipboardList, Lock, Puzzle, RefreshCw } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { FirstDirectorBanner } from '@/features/platform/components/FirstDirectorBanner';

export const PLATFORM_MODULES_PATH = '/platform_admin/modulos';
export const PLATFORM_AUDIT_PATH = '/platform_admin/auditoria';

function StateIcon({ icon: Icon, tone }) {
  const toneClass = tone === 'danger' ? 'bg-danger-bg text-danger-ink' : 'bg-accent-soft text-brand';
  return (
    <span className={`grid h-12 w-12 place-items-center rounded-card ${toneClass}`}>
      <Icon className="h-6 w-6" aria-hidden="true" />
    </span>
  );
}

export function FirstDirectorLoadingCard() {
  return (
    <GlassCard className="flex max-w-3xl flex-col gap-3.5 p-4 sm:p-5" aria-busy="true" aria-label="Cargando">
      <Skeleton className="h-3.5 w-32" />
      <Skeleton className="h-11" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Skeleton className="h-11" />
        <Skeleton className="h-11" />
      </div>
      <Skeleton className="h-11" />
      <Skeleton className="h-11" />
      <p role="status" className="text-size-12-5 text-ink-faint">Revisando si la institución ya tiene Director…</p>
    </GlassCard>
  );
}

export function FirstDirectorLoadErrorCard({ onRetry }) {
  return (
    <GlassCard className="max-w-3xl">
      <EmptyState
        icon={<StateIcon icon={AlertTriangle} tone="danger" />}
        title="No pudimos revisar si ya existe un Director"
        description="Revisá tu conexión y volvé a intentar. No se creó nada."
        action={
          <Button variant="outline" className="h-11" onClick={onRetry}>
            <RefreshCw aria-hidden="true" />
            Reintentar
          </Button>
        }
      />
    </GlassCard>
  );
}

export function FirstDirectorForbiddenCard() {
  return (
    <GlassCard className="max-w-3xl">
      <EmptyState
        icon={<StateIcon icon={Lock} />}
        title="No tenés permiso para esta pantalla"
        description="Solo el administrador de la plataforma puede dar de alta un Director."
        action={
          <Button asChild variant="outline" className="h-11">
            <Link to={PLATFORM_MODULES_PATH}>
              <Puzzle aria-hidden="true" />
              Ir a Módulos
            </Link>
          </Button>
        }
      />
    </GlassCard>
  );
}

export function FirstDirectorExistsCard({ institutionName }) {
  return (
    <GlassCard className="flex max-w-3xl flex-col gap-3 p-4 sm:p-5">
      <FirstDirectorBanner tone="danger" icon={AlertTriangle} title="Ya existe un Director" focusOnMount>
        {institutionName ? `${institutionName} ya tiene su primer Director. ` : 'Esta institución ya tiene su primer Director. '}
        Para sumar más, entrá como Director y usá Personal.
      </FirstDirectorBanner>
      <div>
        <Button asChild variant="outline" className="h-11">
          <Link to={PLATFORM_AUDIT_PATH}>
            <ClipboardList aria-hidden="true" />
            Ver en Auditoría
          </Link>
        </Button>
      </div>
    </GlassCard>
  );
}
