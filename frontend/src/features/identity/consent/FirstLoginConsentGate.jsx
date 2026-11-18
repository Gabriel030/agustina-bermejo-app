import { useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useContactConsents, useUpdateContactConsent } from '@/features/identity/hooks/useContactConsents';
import { InstitutionalNoticesCheckbox } from '@/features/identity/consent/InstitutionalNoticesCheckbox';

const OPEN_PATH_PREFIXES = ['/legal/', '/confirmar-responsable'];

export function FirstLoginConsentGate({ children }) {
  const { usuario, logout } = useAuth();
  const { pathname } = useLocation();
  const { data, isPending, refetch } = useContactConsents({ enabled: Boolean(usuario), retry: false });
  const [accepted, setAccepted] = useState(false);
  const [saveError, setSaveError] = useState(false);
  const errorRef = useRef(null);
  const update = useUpdateContactConsent();
  const isOpenPath = OPEN_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
  const hasNoRecord =
    Array.isArray(data) &&
    data.some((entry) => entry.channel === 'EMAIL' && entry.category === 'INSTITUTIONAL' && entry.hasRecord === false);

  const loading = Boolean(usuario) && !isOpenPath && isPending;
  if (!usuario || isOpenPath || !hasNoRecord) {
    return (
      <div style={{ display: loading ? 'none' : 'contents' }}>
        {children}
      </div>
    );
  }

  async function submit() {
    setSaveError(false);
    try {
      await update.mutateAsync({ channel: 'EMAIL', category: 'INSTITUTIONAL', granted: true, source: 'ACCOUNT_CREATION' });
    } catch (error) {
      if (error.code === 'CONTACT_CONSENT_ALREADY_RECORDED') {
        refetch();
        return;
      }
      setSaveError(true);
      requestAnimationFrame(() => errorRef.current?.focus());
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-8">
      <MeshBackground />
      <GlassCard className="flex w-full max-w-[480px] flex-col gap-4 p-6 sm:p-10">
        <h1 className="font-heading text-lg font-bold text-ink">Antes de seguir, aceptá los avisos de la institución</h1>
        <p className="text-size-13 text-ink-soft">
          Tu cuenta fue creada por la institución. Para continuar, aceptá recibir avisos institucionales.
        </p>
        <InstitutionalNoticesCheckbox id="first-login-notices" checked={accepted} onCheckedChange={setAccepted} />
        {saveError && (
          <p ref={errorRef} tabIndex={-1} role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
            No pudimos guardar tu aceptación. Intentá de nuevo.
          </p>
        )}
        <Button variant="gradient" disabled={!accepted} loading={update.isPending} onClick={submit}>
          Continuar
        </Button>
        <Button variant="ghost" onClick={logout}>
          Cerrar sesión
        </Button>
      </GlassCard>
    </div>
  );
}
