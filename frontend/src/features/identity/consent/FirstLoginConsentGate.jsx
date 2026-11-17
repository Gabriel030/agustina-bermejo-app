import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useContactConsents, useUpdateContactConsent } from '@/features/identity/hooks/useContactConsents';
import { InstitutionalNoticesCheckbox } from '@/features/identity/consent/InstitutionalNoticesCheckbox';

const OPEN_PATH_PREFIXES = ['/legal/', '/confirmar-responsable'];

export function FirstLoginConsentGate({ children }) {
  const { usuario } = useAuth();
  const { pathname } = useLocation();
  const { data } = useContactConsents({ enabled: Boolean(usuario) });
  const [accepted, setAccepted] = useState(false);
  const update = useUpdateContactConsent();
  const isOpenPath = OPEN_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
  const hasNoRecord = Array.isArray(data) && !data.some((entry) => entry.recordedAt);

  if (!usuario || !hasNoRecord || isOpenPath) return children;

  async function submit() {
    try {
      await update.mutateAsync({ channel: 'EMAIL', category: 'INSTITUTIONAL', granted: true });
    } catch {
      toast.error('No pudimos guardar tu aceptación. Intentá de nuevo.');
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
        <Button variant="gradient" disabled={!accepted} loading={update.isPending} onClick={submit}>
          Continuar
        </Button>
      </GlassCard>
    </div>
  );
}
