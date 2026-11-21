import { useSearchParams, Link } from 'react-router-dom';
import { m } from 'motion/react';
import { CheckCircle2, XCircle, Loader2, AlertTriangle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Button } from '@/components/ui/button';
import { useConfirmUnsubscribe, useUnsubscribeLink } from '@/features/identity/hooks/useUnsubscribeLink';
import { CONSENT_CATEGORY_PLURAL_LABELS, CONSENT_CHANNEL_LABELS } from '@/features/identity/lib/contactConsentLabels';

export function BajaComunicaciones() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const link = useUnsubscribeLink(token);
  const confirm = useConfirmUnsubscribe(token);

  const state = !token || link.isError ? 'invalid' : confirm.isSuccess ? 'done' : link.data ? 'ready' : 'loading';
  const channelLabel = CONSENT_CHANNEL_LABELS[link.data?.channel] ?? '';
  const categoryLabel = CONSENT_CATEGORY_PLURAL_LABELS[link.data?.category] ?? '';
  const isInstitutional = link.data?.category === 'INSTITUTIONAL';

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <m.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25 }}
        className="w-full max-w-sm"
      >
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
        </div>

        <GlassCard className="flex flex-col items-center gap-4 p-8 text-center">
          {state === 'loading' && (
            <>
              <Loader2 size={40} className="animate-spin text-brand" />
              <p className="text-size-14 text-ink-soft">Cargando…</p>
            </>
          )}

          {state === 'ready' && (
            <>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Darte de baja</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Vas a dejar de recibir comunicaciones {categoryLabel} por {channelLabel}.
                </p>
              </div>
              {isInstitutional && (
                <div role="alert" className="flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2 text-left text-size-13 text-ink">
                  <AlertTriangle size={18} className="mt-0.5 shrink-0 text-warning" />
                  <span>
                    Dejarías de recibir los avisos institucionales. La cobranza y los avisos obligatorios siguen llegando.
                  </span>
                </div>
              )}
              {confirm.isError && (
                <p role="alert" className="text-size-13 text-danger">
                  No pudimos procesar la baja. Intentá de nuevo.
                </p>
              )}
              <Button variant="primary" onClick={() => confirm.mutate()} loading={confirm.isPending}>
                Confirmar baja
              </Button>
            </>
          )}

          {state === 'done' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-success-bg">
                <CheckCircle2 size={36} className="text-success" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Listo, te diste de baja</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Podés volver a activarlo cuando quieras desde Mi perfil.
                </p>
              </div>
              <Link to="/perfil" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a Mi perfil
              </Link>
            </>
          )}

          {state === 'invalid' && (
            <>
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-danger-bg">
                <XCircle size={36} className="text-danger" />
              </div>
              <div>
                <p className="font-heading text-lg font-bold text-ink">Este enlace no es válido</p>
                <p className="mt-1 text-size-13 text-ink-soft">
                  Si querés cambiar tus preferencias de contacto, entrá a Mi perfil.
                </p>
              </div>
              <Link to="/perfil" className="text-size-13 font-semibold text-brand hover:text-accent-2">
                Ir a Mi perfil
              </Link>
            </>
          )}
        </GlassCard>
      </m.div>
    </div>
  );
}
