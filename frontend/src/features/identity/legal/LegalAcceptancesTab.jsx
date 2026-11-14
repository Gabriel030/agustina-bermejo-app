import { Link } from 'react-router-dom';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Skeleton } from '@/components/Skeleton';
import { StatusPill } from '@/components/StatusPill';
import { LoadErrorState } from '@/components/LoadErrorState';
import {
  useAcceptLegalDocuments,
  useCurrentLegalDocuments,
  useNewsletterPreference,
  usePendingLegalAcceptances,
  useUpdateNewsletterPreference,
} from '@/features/identity/hooks/useLegal';
import { formatLegalDate, formatLegalDateTime, LEGAL_DOCUMENT_LABEL } from '@/features/identity/lib/legalDocuments';

function DocumentsSection() {
  const current = useCurrentLegalDocuments();
  const pending = usePendingLegalAcceptances();
  const accept = useAcceptLegalDocuments();

  if (current.isPending || pending.isPending) return <Skeleton className="h-24 w-full" />;
  if (current.isError || pending.isError) {
    return <LoadErrorState bare error={current.error ?? pending.error} onRetry={() => { current.refetch(); pending.refetch(); }} />;
  }

  const pendingTypes = new Set(pending.data.documents.map((document) => document.type));
  const deadline = formatLegalDate(pending.data.suggestedDeadline);

  return (
    <div className="flex flex-col gap-3">
      <h3 className="text-size-14 font-semibold text-ink">Documentos legales</h3>
      <ul className="flex flex-col gap-2">
        {current.data.documents.map((document) => (
          <li
            key={document.type}
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-4 py-2.5"
          >
            <div>
              <Link to={document.path} className="text-size-13 font-semibold text-brand hover:text-accent-2">
                {LEGAL_DOCUMENT_LABEL[document.type]}
              </Link>
              <div className="text-size-11 text-ink-faint">Versión {document.version}</div>
            </div>
            {pendingTypes.has(document.type) ? (
              <StatusPill tone="warning">Pendiente de aceptar</StatusPill>
            ) : (
              <StatusPill tone="success">Aceptado</StatusPill>
            )}
          </li>
        ))}
      </ul>
      {pending.data.guardianConfirmationRequired ? (
        <p className="text-size-13 text-ink-soft">Tu responsable tiene que confirmar la aceptación desde su correo.</p>
      ) : pendingTypes.size > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-size-13 text-ink-soft">
            Hay versiones nuevas para aceptar.{deadline ? ` Tenés tiempo hasta el ${deadline}.` : ''}
          </p>
          <Button onClick={() => accept.mutate()} disabled={accept.isPending}>
            {accept.isPending ? 'Aceptando…' : 'Aceptar versiones vigentes'}
          </Button>
        </div>
      ) : (
        <p className="text-size-13 text-ink-soft">Aceptaste las versiones vigentes de los dos documentos.</p>
      )}
      {accept.isError && (
        <p role="alert" className="text-size-13 text-danger">
          {describeError(accept.error).message}
        </p>
      )}
    </div>
  );
}

function NewsletterSection() {
  const preference = useNewsletterPreference();
  const update = useUpdateNewsletterPreference();

  if (preference.isPending) return <Skeleton className="h-16 w-full" />;
  if (preference.isError) return <LoadErrorState bare error={preference.error} onRetry={() => preference.refetch()} />;

  return (
    <div className="flex flex-col gap-2 border-t border-border pt-6">
      <h3 className="text-size-14 font-semibold text-ink">Novedades por correo</h3>
      <div className="flex items-start gap-3">
        <Checkbox
          id="newsletter-preference"
          checked={preference.data.optedIn}
          disabled={update.isPending}
          onCheckedChange={(checked) => update.mutate(checked === true)}
        />
        <label htmlFor="newsletter-preference" className="text-size-13 text-ink">
          Quiero recibir novedades del instituto por correo.
          <span className="block text-size-11 text-ink-faint">
            Podés cambiarlo cuando quieras. Última actualización: {formatLegalDateTime(preference.data.updatedAt)}.
          </span>
        </label>
      </div>
      {update.isError && (
        <p role="alert" className="text-size-13 text-danger">
          {describeError(update.error).message}
        </p>
      )}
    </div>
  );
}

export function LegalAcceptancesTab() {
  return (
    <div className="flex flex-col gap-6">
      <DocumentsSection />
      <NewsletterSection />
    </div>
  );
}
