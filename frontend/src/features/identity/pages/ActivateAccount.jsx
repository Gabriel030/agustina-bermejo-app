import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, CircleAlert, Clock, RefreshCw, WifiOff, XCircle } from 'lucide-react';
import { CircuitOpenError, NetworkError, RateLimitedError } from '@/api/client';
import { ERROR_MESSAGES, describeError } from '@/api/describeError';
import { GlassCard } from '@/components/GlassCard';
import { InstitutionBrand } from '@/components/InstitutionBrand';
import { Notice } from '@/components/Notice';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { ActivationPasswordField } from '@/features/identity/components/ActivationPasswordField';
import {
  ACTIVATION_STATUS,
  activateAccount,
  buildMailtoHref,
  fetchActivationStatus,
  isPlausibleActivationToken,
  validateNewPassword,
  validatePasswordConfirmation,
} from '@/features/identity/lib/accountActivation';
import { DEFAULT_COOLDOWN_SECONDS, NETWORK_MESSAGE } from '@/features/identity/lib/loginErrors';
import { useCooldown } from '@/features/identity/lib/useCooldown';

const ACTIVATION_PATH = '/activar';
const INSTITUTION_TITLE = 'Instituto Agustina Bermejo';
const FOCUS_OUTLINE =
  'focus-visible:ring-0 focus-visible:outline-2 focus-visible:outline-solid focus-visible:outline-offset-2 focus-visible:outline-(--focus-ring)';
const COUNTDOWN_STYLE =
  'aria-disabled:cursor-not-allowed aria-disabled:border-border aria-disabled:bg-none aria-disabled:bg-neutral-bg aria-disabled:text-ink-soft aria-disabled:shadow-none aria-disabled:hover:translate-y-0 aria-disabled:hover:bg-none aria-disabled:hover:shadow-none';
const WRAP = 'min-w-0 [overflow-wrap:anywhere]';

function isConnectionFailure(error) {
  return error instanceof NetworkError || error instanceof CircuitOpenError;
}

function StateHeading({ headingRef, children }) {
  return (
    <h1 ref={headingRef} tabIndex={-1} className="font-heading text-size-22 font-bold text-ink outline-none">
      {children}
    </h1>
  );
}

function StateIcon({ tone, children }) {
  const tones = { success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger' };
  return (
    <span className={`flex size-12 items-center justify-center rounded-full text-on-accent ${tones[tone]}`} aria-hidden="true">
      {children}
    </span>
  );
}

function LoginLink({ variant = 'outline' }) {
  return (
    <Button asChild variant={variant} className={`h-11 w-full ${FOCUS_OUTLINE}`}>
      <Link to="/login">Ir a iniciar sesión</Link>
    </Button>
  );
}

function InstitutionContact({ institution }) {
  if (!institution) return null;
  const { name, email, phoneNumber } = institution;
  const mailtoHref = buildMailtoHref(email);
  if (!name && !email && !phoneNumber) return null;
  return (
    <div className="flex flex-col gap-1 rounded-xl bg-neutral-bg px-3 py-2.5 text-size-13">
      {name && <b className="font-bold text-ink">{name}</b>}
      {email && mailtoHref && (
        <a href={mailtoHref} className={`flex min-h-11 items-center font-semibold text-brand ${WRAP}`}>
          {email}
        </a>
      )}
      {email && !mailtoHref && <span className={`font-semibold text-ink ${WRAP}`}>{email}</span>}
      {phoneNumber && (
        <a href={`tel:${phoneNumber.replace(/[^\d+]/g, '')}`} className={`flex min-h-11 items-center font-semibold text-brand ${WRAP}`}>
          {phoneNumber}
        </a>
      )}
    </div>
  );
}

function LoadingCard() {
  return (
    <GlassCard className="flex flex-col gap-4 p-5.5" role="status" aria-busy="true" aria-label="Verificando el enlace">
      <Skeleton className="h-7 w-2/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-11 w-full" />
      <Skeleton className="h-11 w-full" />
      <Skeleton className="h-11 w-full" />
    </GlassCard>
  );
}

function InvalidCard({ headingRef }) {
  return (
    <GlassCard className="flex flex-col gap-3.5 p-5.5">
      <StateIcon tone="danger">
        <XCircle size={24} />
      </StateIcon>
      <StateHeading headingRef={headingRef}>Enlace no válido</StateHeading>
      <p className="text-ink-soft">{ERROR_MESSAGES.ACTIVATION_TOKEN_INVALID}</p>
      <p className="text-ink-soft">Si ya activaste tu cuenta, entrá con tu usuario y tu contraseña.</p>
      <LoginLink />
    </GlassCard>
  );
}

function ExpiredCard({ headingRef, institution }) {
  return (
    <GlassCard className="flex flex-col gap-3.5 p-5.5">
      <StateIcon tone="warning">
        <Clock size={24} />
      </StateIcon>
      <StateHeading headingRef={headingRef}>El enlace venció</StateHeading>
      <p className="text-ink-soft">{ERROR_MESSAGES.ACTIVATION_TOKEN_EXPIRED}</p>
      <p className="text-ink-soft">Escribile a la secretaría del instituto y pedí que te manden una invitación nueva.</p>
      <InstitutionContact institution={institution} />
      <LoginLink />
    </GlassCard>
  );
}

function StatusFailureCard({ headingRef, rateLimited, secondsLeft, onRetry }) {
  const waiting = rateLimited && secondsLeft > 0;
  return (
    <GlassCard className="flex flex-col gap-3.5 p-5.5">
      <StateIcon tone="danger">{rateLimited ? <AlertTriangle size={24} /> : <WifiOff size={24} />}</StateIcon>
      <StateHeading headingRef={headingRef}>{rateLimited ? 'Demasiados intentos' : 'No pudimos verificar el enlace'}</StateHeading>
      <p role="alert" className="text-ink-soft">
        {rateLimited ? 'Esperá un momento para volver a probar.' : NETWORK_MESSAGE}
      </p>
      <Button
        type="button"
        variant="primary"
        className={`h-11 w-full ${FOCUS_OUTLINE} ${COUNTDOWN_STYLE}`}
        aria-disabled={waiting || undefined}
        onClick={() => {
          if (!waiting) onRetry();
        }}
      >
        <RefreshCw aria-hidden="true" />
        {waiting ? `Reintentar en ${secondsLeft} s` : 'Reintentar'}
      </Button>
    </GlassCard>
  );
}

function SuccessCard({ headingRef, username }) {
  return (
    <GlassCard className="flex flex-col gap-3.5 p-5.5">
      <StateIcon tone="success">
        <CheckCircle2 size={24} />
      </StateIcon>
      <StateHeading headingRef={headingRef}>Cuenta activada</StateHeading>
      <p className="text-ink-soft">Ya podés iniciar sesión con tu usuario y la contraseña que elegiste.</p>
      {username && (
        <p className="text-ink-soft">
          Tu usuario para ingresar es <b className={`font-bold text-ink ${WRAP}`}>{username}</b>
        </p>
      )}
      <LoginLink variant="primary" />
    </GlassCard>
  );
}

function ActivationForm({ headingRef, token, username, onTokenRejected, onActivated }) {
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [touched, setTouched] = useState({ password: false, confirmation: false });
  const [visible, setVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverPasswordError, setServerPasswordError] = useState(null);
  const [notice, setNotice] = useState(null);
  const cooldown = useCooldown();
  const mountedRef = useRef(true);
  const formRef = useRef(null);
  const passwordRef = useRef(null);
  const submitRef = useRef(null);
  const pendingFocusRef = useRef(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (submitting || !pendingFocusRef.current) return;
    const target = pendingFocusRef.current === 'password' ? passwordRef : submitRef;
    pendingFocusRef.current = null;
    target.current?.focus();
  }, [submitting]);

  const passwordError = (touched.password && validateNewPassword(password)) || serverPasswordError;
  const confirmationError = touched.confirmation ? validatePasswordConfirmation(password, confirmation) : null;
  const blocked = submitting || cooldown.active;

  async function handleSubmit(event) {
    event.preventDefault();
    if (blocked) return;
    setTouched({ password: true, confirmation: true });
    setNotice(null);
    setServerPasswordError(null);
    if (validateNewPassword(password) || validatePasswordConfirmation(password, confirmation)) {
      queueMicrotask(() => formRef.current?.querySelector('[aria-invalid="true"]')?.focus());
      return;
    }
    setSubmitting(true);
    try {
      await activateAccount({ token, newPassword: password });
      onActivated();
    } catch (error) {
      if (!mountedRef.current) return;
      handleSubmitError(error);
    } finally {
      if (mountedRef.current) setSubmitting(false);
    }
  }

  function handleSubmitError(error) {
    pendingFocusRef.current = 'submit';
    if (error?.code === 'ACTIVATION_TOKEN_INVALID') return onTokenRejected(ACTIVATION_STATUS.INVALID);
    if (error?.code === 'ACTIVATION_TOKEN_EXPIRED') return onTokenRejected(ACTIVATION_STATUS.EXPIRED);
    if (error?.code === 'INVALID_PASSWORD_POLICY') {
      setServerPasswordError(describeError(error).message);
      pendingFocusRef.current = 'password';
      return;
    }
    if (error instanceof RateLimitedError) {
      cooldown.start(error.retryAfterSeconds ?? DEFAULT_COOLDOWN_SECONDS);
      setNotice({ message: 'Demasiados intentos. Esperá para volver a probar.', retry: true });
      return;
    }
    if (isConnectionFailure(error)) {
      setNotice({ message: NETWORK_MESSAGE, retry: true });
      return;
    }
    setNotice({ message: describeError(error).message, retry: false });
  }

  let submitLabel = 'Activar cuenta';
  if (submitting) submitLabel = 'Activando…';
  else if (cooldown.active) submitLabel = `Reintentar en ${cooldown.secondsLeft} s`;
  else if (notice?.retry) submitLabel = 'Reintentar';

  return (
    <GlassCard className="p-5.5">
      <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={submitting} className="flex flex-col gap-3.5">
        <StateHeading headingRef={headingRef}>Activá tu cuenta</StateHeading>
        <p className="text-ink-soft">Elegí una contraseña para entrar al sistema del instituto.</p>
        {username && (
          <p className="text-ink-soft">
            Tu usuario para ingresar es <b className={`font-bold text-ink ${WRAP}`}>{username}</b>
          </p>
        )}
        {notice && (
          <Notice tone="danger" role="alert" className="flex items-start gap-2">
            <CircleAlert size={16} className="mt-px shrink-0 text-danger" aria-hidden="true" />
            <span>{notice.message}</span>
          </Notice>
        )}
        <ActivationPasswordField
          id="activation-password"
          label="Nueva contraseña"
          value={password}
          onChange={(value) => {
            setPassword(value);
            setServerPasswordError(null);
          }}
          onBlur={() => setTouched((current) => ({ ...current, password: true }))}
          error={passwordError}
          hint="Mínimo 8 caracteres, con una mayúscula y un número."
          disabled={submitting}
          visible={visible}
          onToggleVisible={() => setVisible((current) => !current)}
          inputRef={passwordRef}
        />
        <ActivationPasswordField
          id="activation-password-confirmation"
          label="Repetir contraseña"
          value={confirmation}
          onChange={setConfirmation}
          onBlur={() => setTouched((current) => ({ ...current, confirmation: true }))}
          error={confirmationError}
          disabled={submitting}
          visible={visible}
          onToggleVisible={() => setVisible((current) => !current)}
        />
        <Button
          ref={submitRef}
          type="submit"
          variant="primary"
          className={`h-11 w-full ${FOCUS_OUTLINE} ${COUNTDOWN_STYLE}`}
          loading={submitting}
          aria-disabled={cooldown.active || undefined}
        >
          {!submitting && notice?.retry && !cooldown.active && <RefreshCw aria-hidden="true" />}
          {submitLabel}
        </Button>
      </form>
    </GlassCard>
  );
}

export function ActivateAccount() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => searchParams.get('token'));
  const [phase, setPhase] = useState(() => (isPlausibleActivationToken(token) ? 'loading' : ACTIVATION_STATUS.INVALID));
  const [info, setInfo] = useState({ username: null, institution: null });
  const [attempt, setAttempt] = useState(0);
  const statusCooldown = useCooldown();
  const startStatusCooldown = statusCooldown.start;
  const headingRef = useRef(null);
  const previousPhaseRef = useRef(phase);

  useEffect(() => {
    if (searchParams.has('token')) navigate(ACTIVATION_PATH, { replace: true });
  }, [searchParams, navigate]);

  useEffect(() => {
    if (!isPlausibleActivationToken(token)) return undefined;
    const controller = new AbortController();
    fetchActivationStatus(token, controller.signal)
      .then((result) => {
        setInfo({ username: result.username, institution: result.institution });
        setPhase(result.status);
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RateLimitedError) {
          startStatusCooldown(error.retryAfterSeconds ?? DEFAULT_COOLDOWN_SECONDS);
          setPhase('rate-limited');
          return;
        }
        setPhase('status-failed');
      });
    return () => controller.abort();
  }, [token, attempt, startStatusCooldown]);

  useEffect(() => {
    if (previousPhaseRef.current !== phase && phase !== 'loading') headingRef.current?.focus();
    previousPhaseRef.current = phase;
  }, [phase]);

  function retryStatus() {
    setPhase('loading');
    setAttempt((current) => current + 1);
  }

  function handleTokenRejected(status) {
    setPhase(status);
  }

  let content;
  if (phase === 'loading') content = <LoadingCard />;
  else if (phase === ACTIVATION_STATUS.VALID) {
    content = (
      <ActivationForm
        headingRef={headingRef}
        token={token}
        username={info.username}
        onTokenRejected={handleTokenRejected}
        onActivated={() => setPhase('activated')}
      />
    );
  } else if (phase === 'activated') content = <SuccessCard headingRef={headingRef} username={info.username} />;
  else if (phase === ACTIVATION_STATUS.EXPIRED) content = <ExpiredCard headingRef={headingRef} institution={info.institution} />;
  else if (phase === 'rate-limited' || phase === 'status-failed') {
    content = (
      <StatusFailureCard
        headingRef={headingRef}
        rateLimited={phase === 'rate-limited'}
        secondsLeft={statusCooldown.secondsLeft}
        onRetry={retryStatus}
      />
    );
  } else content = <InvalidCard headingRef={headingRef} />;

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-[460px] flex-col justify-center gap-4 px-4 py-6">
      <InstitutionBrand layout="horizontal" size="sm" titulo={INSTITUTION_TITLE} className="justify-center" />
      {content}
      <p className="text-center text-size-12-5 text-ink-faint">{INSTITUTION_TITLE} · Activación de cuenta</p>
    </div>
  );
}
