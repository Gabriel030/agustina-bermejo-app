import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { describeError } from '@/api/describeError';
import { Button } from '@/components/ui/button';
import { useAcceptLegalDocuments, usePendingLegalAcceptances } from '@/features/identity/hooks/useLegal';
import { formatLegalDate, LEGAL_DOCUMENT_LABEL } from '@/features/identity/lib/legalDocuments';

export function LegalNoticeBanner() {
  const { usuario } = useAuth();
  const { data } = usePendingLegalAcceptances(Boolean(usuario));
  const accept = useAcceptLegalDocuments();
  const [dismissed, setDismissed] = useState(false);

  if (!data || data.guardianConfirmationRequired || data.documents.length === 0 || dismissed) return null;

  const deadline = formatLegalDate(data.suggestedDeadline);
  const deadlineText = data.gracePeriodExpired
    ? 'El plazo ya pasó. Podés seguir usando el sistema, pero te pedimos que los aceptes cuanto antes.'
    : deadline
      ? `Tenés tiempo hasta el ${deadline}.`
      : '';

  return (
    <section
      aria-labelledby="legal-notice-title"
      className="flex shrink-0 flex-wrap items-center justify-between gap-3 rounded-xl border border-brand bg-accent-soft px-4 py-3"
    >
      <div className="min-w-0 flex-1 basis-72">
        <strong id="legal-notice-title" className="block text-size-14 font-bold text-ink">
          Aceptá los términos actualizados
        </strong>
        <p className="mt-0.5 text-size-13 text-ink-soft">
          Actualizamos{' '}
          {data.documents.map((document, index) => (
            <span key={document.type}>
              {index > 0 && ' y '}
              <Link to={document.path} target="_blank" rel="noopener" className="font-semibold text-brand hover:text-accent-2">
                {LEGAL_DOCUMENT_LABEL[document.type]}
              </Link>
            </span>
          ))}
          . {deadlineText} Los tenés siempre en{' '}
          <Link to="/perfil" className="font-semibold text-brand hover:text-accent-2">
            Mi perfil
          </Link>
          .
        </p>
        {accept.isError && (
          <p role="alert" className="mt-1 text-size-12-5 font-medium text-danger">
            {describeError(accept.error).message}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button size="lg" onClick={() => accept.mutate()} disabled={accept.isPending}>
          {accept.isPending ? 'Aceptando…' : 'Aceptar'}
        </Button>
        <Button size="lg" variant="outline" onClick={() => setDismissed(true)}>
          Más tarde
        </Button>
      </div>
    </section>
  );
}
