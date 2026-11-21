import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { m } from 'motion/react';
import { CheckCircle2, Clock, Mail, Shield, TriangleAlert, WifiOff, XCircle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { apiFetch } from '@/api/client';
import { describeError } from '@/api/describeError';
import { roleLabel } from '@/lib/activeRole';
import { EmailChangeComparisonTable } from '@/features/identity/pages/EmailChangeComparisonTable';
import { EmailChangeResultCard } from '@/features/identity/pages/EmailChangeResultCard';

const HOUR_MS = 60 * 60 * 1000;
const ALERT_PHASES = ['invalid', 'notFound', 'conflict', 'failed', 'offline'];
const CANCEL_PATH = '/cancelar-cambio-email';
const REVIEW_PATH = '/revisar-cambio-email';

function postToken(path, token) {
  return apiFetch(path, { method: 'POST', body: JSON.stringify({ token }) });
}

function requestedMoment(value) {
  const [date, time = ''] = value.split('T');
  const [, month, day] = date.split('-');
  return `${day}/${month} a las ${time.slice(0, 5)}`;
}

function expiryLabel(value) {
  const remaining = new Date(value).getTime() - Date.now();
  return remaining <= 0 ? 'Venció' : `Vence en ${Math.max(1, Math.ceil(remaining / HOUR_MS))} h`;
}

function failurePhase(error) {
  if (error?.status === 400) return 'invalid';
  if (error?.status === 404) return 'notFound';
  if (error?.status === 409) return 'conflict';
  if (!error?.status || error.status >= 500 || describeError(error).retryable) return 'offline';
  return 'failed';
}

function StepDots({ current }) {
  return (
    <div className="flex items-center gap-1.5 text-size-12 text-ink-faint">
      <span className="h-2 w-2 rounded-full bg-brand" aria-hidden="true" />
      <span className={`h-2 w-2 rounded-full ${current === 2 ? 'bg-brand' : 'bg-border'}`} aria-hidden="true" />
      <span>Paso {current} de 2</span>
    </div>
  );
}

function LoginButton() {
  return (
    <Button asChild variant="outline" className="h-11 w-full">
      <Link to="/login">Ir a ingresar</Link>
    </Button>
  );
}

export function ReviewEmailChangePage({ mode = 'review' }) {
  const cancelOnly = mode === 'cancel';
  const path = cancelOnly ? CANCEL_PATH : REVIEW_PATH;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => searchParams.get('token'));
  const messageRef = useRef(null);
  const submittingRef = useRef(false);
  const [phase, setPhase] = useState(!token ? 'invalid' : cancelOnly ? 'cancelPrompt' : 'loading');
  const [preview, setPreview] = useState(null);
  const [submitting, setSubmitting] = useState(null);
  const [retryAction, setRetryAction] = useState(null);

  const loadPreview = useCallback(() => {
    postToken('/email-change-preview', token)
      .then((data) => {
        setPreview(data);
        setPhase('review');
      })
      .catch((error) => {
        setRetryAction('preview');
        setPhase(failurePhase(error));
      });
  }, [token]);

  useEffect(() => {
    navigate(path, { replace: true });
  }, [navigate, path]);

  useEffect(() => {
    if (token && !cancelOnly) loadPreview();
  }, [token, cancelOnly, loadPreview]);

  useEffect(() => {
    if (phase !== 'loading' && phase !== 'cancelPrompt') messageRef.current?.focus();
  }, [phase]);

  function submit(action) {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(action);
    postToken(action === 'approve' ? '/approve-email-change' : '/cancel-email-change', token)
      .then(() => setPhase(action === 'approve' ? 'approved' : 'cancelled'))
      .catch((error) => {
        setRetryAction(action);
        setPhase(failurePhase(error));
      })
      .finally(() => {
        submittingRef.current = false;
        setSubmitting(null);
      });
  }

  function retry() {
    if (retryAction === 'preview') {
      setPhase('loading');
      loadPreview();
      return;
    }
    submit(retryAction);
  }

  const isAlert = ALERT_PHASES.includes(phase);

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center p-4">
      <m.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25 }} className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3">
          <InstitutionBrand />
        </div>

        <GlassCard
          ref={messageRef}
          tabIndex={-1}
          role={isAlert ? 'alert' : 'status'}
          aria-live={isAlert ? 'assertive' : 'polite'}
          aria-busy={phase === 'loading'}
          aria-label={phase === 'loading' ? 'Cargando el cambio de email' : undefined}
          className="flex flex-col items-center gap-4 p-7 text-center outline-none"
        >
          {phase === 'loading' && (
            <>
              <Skeleton className="h-2.5 w-30" />
              <Skeleton className="h-4.5 w-50" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-11 w-full" />
              <Skeleton className="h-11 w-full" />
            </>
          )}

          {phase === 'cancelPrompt' && (
            <EmailChangeResultCard
              tone="warning"
              icon={Shield}
              title="¿No pediste cambiar tu email?"
              description="Si no fuiste vos, cancelá el cambio y tu cuenta seguirá con el mismo correo."
              action={
                <Button variant="primary" className="h-11 w-full" loading={submitting === 'cancel'} disabled={submitting !== null} onClick={() => submit('cancel')}>
                  Cancelar el cambio
                </Button>
              }
            />
          )}

          {phase === 'review' && (
            <>
              <StepDots current={1} />
              <div>
                <p className="font-heading text-lg font-bold text-ink">¿Cambiamos tu email?</p>
                <p className="mt-1 text-size-13 leading-snug text-ink-soft">
                  Lo pidió {roleLabel(preview.requestedByRole)} el {requestedMoment(preview.requestedAt)}.
                  <br />
                  ¿No lo reconocés? Cancelá.
                </p>
              </div>
              <EmailChangeComparisonTable
                caption="Cambio de email pedido"
                columns={['Email']}
                rows={[
                  { label: 'Hoy', cells: [preview.currentEmailMasked], emphasis: false },
                  { label: 'Nuevo', cells: [preview.newEmailMasked], emphasis: true },
                ]}
              />
              <div className="flex w-full flex-col gap-2.5">
                <Button variant="primary" className="h-11 w-full" disabled={submitting !== null} onClick={() => setPhase('confirm')}>
                  Revisar y aprobar
                </Button>
                <Button variant="outline" className="h-11 w-full" loading={submitting === 'cancel'} disabled={submitting !== null} onClick={() => submit('cancel')}>
                  <Shield aria-hidden="true" />
                  No lo pedí, cancelar
                </Button>
              </div>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-neutral-bg px-2.5 py-1 text-size-12 font-semibold text-ink-soft">
                <Clock size={14} aria-hidden="true" />
                {expiryLabel(preview.expiresAt)}
              </span>
            </>
          )}

          {phase === 'confirm' && (
            <>
              <StepDots current={2} />
              <EmailChangeResultCard
                tone="warning"
                icon={TriangleAlert}
                title="Confirmá el cambio"
                description="Te mandamos un enlace al email nuevo."
              />
              <EmailChangeComparisonTable
                caption="Qué cambia si aprobás"
                columns={['Hoy', 'Nuevo']}
                rows={[
                  { label: 'Email', cells: [preview.currentEmailMasked, preview.newEmailMasked] },
                  { label: 'Sesiones', cells: ['Abiertas', 'Se cierran al verificar'] },
                ]}
              />
              <div className="flex w-full flex-col gap-2.5">
                <Button variant="primary" className="h-11 w-full" loading={submitting === 'approve'} disabled={submitting !== null} onClick={() => submit('approve')}>
                  Aprobar cambio
                </Button>
                <Button variant="outline" className="h-11 w-full" disabled={submitting !== null} onClick={() => setPhase('review')}>
                  Volver
                </Button>
              </div>
            </>
          )}

          {phase === 'approved' && (
            <EmailChangeResultCard
              tone="info"
              icon={Mail}
              title="Falta un paso"
              description={`Abrí el enlace que enviamos a ${preview.newEmailMasked}.`}
              action={<LoginButton />}
            />
          )}

          {phase === 'cancelled' && (
            <EmailChangeResultCard
              tone="success"
              icon={CheckCircle2}
              title="Cambio cancelado"
              description={preview ? `Tu cuenta sigue con ${preview.currentEmailMasked}.` : 'Tu cuenta sigue con el mismo correo.'}
              action={<LoginButton />}
            />
          )}

          {phase === 'invalid' && (
            <EmailChangeResultCard
              tone="danger"
              icon={XCircle}
              title="Enlace no válido"
              description="Venció o ya se usó. Si no fuiste vos, revisalo en Mi perfil."
              action={<LoginButton />}
            />
          )}

          {phase === 'notFound' && (
            <EmailChangeResultCard
              tone="danger"
              icon={XCircle}
              title="No encontramos el cambio"
              description="Puede que ya se haya resuelto o que el enlace sea incorrecto. Si no fuiste vos, revisalo en Mi perfil."
              action={<LoginButton />}
            />
          )}

          {phase === 'conflict' && (
            <EmailChangeResultCard
              tone="danger"
              icon={XCircle}
              title="El cambio ya no se puede resolver"
              description="Ya se resolvió o está en otro estado. Revisalo en Mi perfil."
              action={<LoginButton />}
            />
          )}

          {phase === 'failed' && (
            <EmailChangeResultCard
              tone="danger"
              icon={XCircle}
              title="No pudimos completar la acción"
              description="Probá de nuevo más tarde. Si no fuiste vos, revisalo en Mi perfil."
              action={<LoginButton />}
            />
          )}

          {phase === 'offline' && (
            <EmailChangeResultCard
              tone="danger"
              icon={WifiOff}
              title="Sin conexión"
              description="Revisá tu conexión."
              action={
                <Button variant="outline" className="h-11 w-full" onClick={retry}>
                  Reintentar
                </Button>
              }
            />
          )}
        </GlassCard>
      </m.div>
    </div>
  );
}
