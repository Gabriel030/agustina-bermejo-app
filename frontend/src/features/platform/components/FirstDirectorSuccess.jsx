import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, CheckIcon, ClipboardList, Clock, Mail, Puzzle } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import { documentLabel } from '@/lib/documentType';
import { FirstDirectorBanner } from '@/features/platform/components/FirstDirectorBanner';
import { PLATFORM_AUDIT_PATH, PLATFORM_MODULES_PATH } from '@/features/platform/components/FirstDirectorStateCard';

const TIMELINE_DOT_CLASS = {
  done: 'bg-success-bg text-success-ink',
  now: 'border border-brand bg-accent-soft text-brand',
  pending: 'bg-neutral-bg text-neutral-ink',
};

function TimelineStep({ index, state, title, detail, pill }) {
  return (
    <li className="relative grid grid-cols-[28px_minmax(0,1fr)] items-start gap-x-3 py-2.5 sm:grid-cols-[28px_minmax(0,1fr)_auto]">
      <span className={`grid h-7 w-7 place-items-center rounded-full text-size-13 font-extrabold ${TIMELINE_DOT_CLASS[state]}`}>
        {state === 'done' ? <CheckIcon className="h-4 w-4" aria-hidden="true" /> : index}
      </span>
      <div className="min-w-0">
        <b className="block text-ink">{title}</b>
        <small className="text-size-12-5 text-ink-faint">{detail}</small>
      </div>
      <div className="col-start-2 mt-1 sm:col-start-auto sm:mt-0">{pill}</div>
    </li>
  );
}

function SummaryItem({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-extrabold uppercase tracking-wide text-ink-faint">{label}</dt>
      <dd className="mt-0.5 break-words font-bold text-ink">{children}</dd>
    </div>
  );
}

export function FirstDirectorSuccess({ institutionName, directorName, documentType, documentNumber, directorEmail }) {
  const focusRef = useRef(null);

  useEffect(() => {
    focusRef.current?.focus();
  }, []);

  return (
    <GlassCard className="flex max-w-3xl flex-col gap-5 p-4 sm:p-5">
      <div ref={focusRef} tabIndex={-1} className="rounded-card outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(--focus-ring)">
        <FirstDirectorBanner tone="success" icon={CheckCircle2} title="Director creado">
          Estamos enviando un email con el código para crear su contraseña a {directorEmail}. El código vale 15 minutos; si no llega, puede usar Recuperar contraseña desde el login.
        </FirstDirectorBanner>
      </div>
      <dl className="grid gap-3 sm:grid-cols-2">
        <SummaryItem label="Institución">{institutionName}</SummaryItem>
        <SummaryItem label="Director">{directorName}</SummaryItem>
        <SummaryItem label="Documento"><span className="tabular-nums">{documentLabel(documentType)} {documentNumber}</span></SummaryItem>
        <SummaryItem label="Email">{directorEmail}</SummaryItem>
      </dl>
      <div>
        <h2 className="mb-1.5 font-heading text-size-15 font-semibold text-ink">Qué pasa ahora</h2>
        <ol className="flex flex-col">
          <TimelineStep
            index={1}
            state="done"
            title="Alta registrada"
            detail="Quedó registrado en la Auditoría."
            pill={<StatusPill tone="success" icon={CheckIcon}>Hecho</StatusPill>}
          />
          <TimelineStep
            index={2}
            state="now"
            title="Email con el código"
            detail="Puede tardar unos minutos."
            pill={<StatusPill tone="accent" icon={Mail}>Enviando</StatusPill>}
          />
          <TimelineStep
            index={3}
            state="pending"
            title="El Director crea su contraseña"
            detail="El código vale 15 minutos."
            pill={<StatusPill tone="neutral" icon={Clock}>Pendiente</StatusPill>}
          />
        </ol>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline" className="h-11">
          <Link to={PLATFORM_AUDIT_PATH}>
            <ClipboardList aria-hidden="true" />
            Ver en Auditoría
          </Link>
        </Button>
        <Button asChild variant="outline" className="h-11">
          <Link to={PLATFORM_MODULES_PATH}>
            <Puzzle aria-hidden="true" />
            Ir a Módulos
          </Link>
        </Button>
      </div>
    </GlassCard>
  );
}
