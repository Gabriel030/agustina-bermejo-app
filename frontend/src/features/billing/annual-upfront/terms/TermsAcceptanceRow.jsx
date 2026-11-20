import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { termsPathOf } from './annualTerms';

export function TermsAcceptanceRow({ terms, checked, onCheckedChange }) {
  if (terms.isPending) return <Skeleton className="h-5 w-full" aria-label="Cargando los términos" />;
  if (terms.isError) {
    return (
      <div className="flex items-center justify-between gap-2 text-sm text-danger">
        <span role="alert">No pudimos cargar los términos.</span>
        <Button variant="outline" size="sm" onClick={() => terms.refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }
  const { versionNumber, version } = terms.data;
  return (
    <div className="flex items-start gap-2">
      <Checkbox
        id="annual-terms"
        aria-describedby="annual-terms-link"
        checked={checked}
        onCheckedChange={(value) => onCheckedChange(value === true)}
      />
      <div className="flex flex-col gap-0.5 text-size-13 leading-snug">
        <Label htmlFor="annual-terms" className="font-normal text-ink-soft">
          Acepto los términos y condiciones del pago anual
        </Label>
        <Link
          id="annual-terms-link"
          to={termsPathOf(versionNumber)}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex w-fit items-center gap-1 font-semibold text-primary hover:underline"
        >
          Leer los términos ({version})
          <ExternalLink size={12} aria-hidden="true" />
        </Link>
      </div>
    </div>
  );
}
