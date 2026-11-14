import { useState } from 'react';
import { MailCheck } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { MeshBackground } from '@/components/MeshBackground';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { RateLimitedError } from '@/api/client';
import { describeError } from '@/api/describeError';
import { useQueryClient } from '@tanstack/react-query';
import { useCooldown } from '@/features/identity/lib/useCooldown';
import { GUARDIAN_RESEND_FALLBACK_SECONDS } from '@/features/identity/lib/legalDocuments';
import { LEGAL_KEYS, useResendGuardianConfirmation } from '@/features/identity/hooks/useLegal';

function formatWait(seconds) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return `${minutes}:${String(rest).padStart(2, '0')}`;
}

export function GuardianWaitingScreen() {
  const { logout } = useAuth();
  const queryClient = useQueryClient();
  const resend = useResendGuardianConfirmation();
  const cooldown = useCooldown();
  const [message, setMessage] = useState(null);

  async function handleResend() {
    setMessage(null);
    try {
      await resend.mutateAsync();
      cooldown.start(GUARDIAN_RESEND_FALLBACK_SECONDS);
      setMessage({ tone: 'success', text: 'Listo, le reenviamos el correo a tu responsable.' });
    } catch (error) {
      if (error instanceof RateLimitedError) {
        cooldown.start(error.retryAfterSeconds ?? GUARDIAN_RESEND_FALLBACK_SECONDS);
        setMessage({ tone: 'error', text: 'Ya enviamos el correo hace poco. Esperá antes de pedirlo otra vez.' });
        return;
      }
      if (error?.code === 'GUARDIAN_CONFIRMATION_NOT_APPLICABLE') {
        await queryClient.invalidateQueries({ queryKey: LEGAL_KEYS.pending });
        return;
      }
      setMessage({ tone: 'error', text: describeError(error).message });
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center px-4 py-8">
      <MeshBackground />
      <GlassCard className="flex w-full max-w-[480px] flex-col items-center gap-4 p-6 text-center sm:p-10">
        <InstitutionBrand />
        <div className="flex size-14 items-center justify-center rounded-full bg-accent-soft">
          <MailCheck className="size-7 text-brand" aria-hidden="true" />
        </div>
        <h1 className="font-heading text-xl font-bold text-ink">Tu responsable tiene que confirmar</h1>
        <p className="text-size-13 text-ink-soft">
          Como sos menor de 18 años, tu madre, padre o tutor tiene que confirmar los términos y la política de
          privacidad desde el correo que le enviamos. Hasta entonces no podés usar el sistema.
        </p>
        <div aria-live="polite" className="min-h-5 text-size-12-5 font-medium">
          {message && <span className={message.tone === 'success' ? 'text-success' : 'text-danger'}>{message.text}</span>}
        </div>
        <Button size="lg" onClick={handleResend} disabled={resend.isPending || cooldown.active} className="w-full">
          {resend.isPending
            ? 'Enviando…'
            : cooldown.active
              ? `Reenviar correo en ${formatWait(cooldown.secondsLeft)}`
              : 'Reenviar correo al responsable'}
        </Button>
        <Button variant="outline" onClick={logout} className="w-full">
          Salir
        </Button>
      </GlassCard>
    </div>
  );
}
