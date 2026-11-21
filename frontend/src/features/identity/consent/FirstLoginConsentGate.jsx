import { useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useContactConsents, useUpdateContactConsent } from '@/features/identity/hooks/useContactConsents';
import { InstitutionalNoticesCheckbox } from '@/features/identity/consent/InstitutionalNoticesCheckbox';

const OPEN_PATH_PREFIXES = ['/legal/', '/confirmar-responsable', '/login', '/registrarse'];

export function FirstLoginConsentGate({ children }) {
  const { usuario, logout } = useAuth();
  const { pathname } = useLocation();
  const { data, isPending } = useContactConsents({ enabled: Boolean(usuario), retry: false });
  const [accepted, setAccepted] = useState(false);
  const [saveError, setSaveError] = useState(false);
  const errorRef = useRef(null);
  const update = useUpdateContactConsent();
  const isOpenPath = OPEN_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
  const hasNoRecord =
    Array.isArray(data) &&
    data.some((entry) => entry.channel === 'EMAIL' && entry.category === 'INSTITUTIONAL' && entry.hasRecord === false);

  if (usuario && !isOpenPath && isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Cargando">
        <Loader2 className="size-6 animate-spin text-ink-soft" />
      </div>
    );
  }
  if (!usuario || isOpenPath || !hasNoRecord) return children;

  async function submit() {
    setSaveError(false);
    try {
      await update.mutateAsync({ channel: 'EMAIL', category: 'INSTITUTIONAL', granted: true });
    } catch {
      setSaveError(true);
      requestAnimationFrame(() => errorRef.current?.focus());
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-8">
      <GlassCard className="flex w-full max-w-[480px] flex-col gap-4 p-6 sm:p-10">
        <h1 className="font-heading text-lg font-bold text-ink">Antes de seguir, aceptá los avisos de la institución</h1>
        <p className="text-size-13 text-ink-soft">
          Para continuar, aceptá recibir los avisos institucionales.
        </p>
        <InstitutionalNoticesCheckbox id="first-login-notices" checked={accepted} onCheckedChange={setAccepted} />
        {saveError && (
          <p ref={errorRef} tabIndex={-1} role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
            No pudimos guardar tu aceptación. Intentá de nuevo.
          </p>
        )}
        <p id="first-login-hint" className="text-size-12 text-ink-soft">
          {accepted ? '' : 'Marcá la casilla para poder continuar.'}
        </p>
        <Button variant="primary" aria-describedby="first-login-hint" disabled={!accepted} loading={update.isPending} onClick={submit}>
          Continuar
        </Button>
        <Button variant="ghost" onClick={logout}>
          Cerrar sesión
        </Button>
      </GlassCard>
    </div>
  );
}
