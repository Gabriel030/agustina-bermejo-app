import { Checkbox } from '@/components/ui/checkbox';
import { LEGAL_DOCUMENT_LABEL, LEGAL_DOCUMENT_PATH } from '@/features/identity/lib/legalDocuments';

const LINK_CLASS = 'font-semibold text-brand';

function LegalLink({ type }) {
  return (
    <a href={LEGAL_DOCUMENT_PATH[type]} target="_blank" rel="noopener noreferrer" className={LINK_CLASS}>
      {LEGAL_DOCUMENT_LABEL[type]}
    </a>
  );
}

export function InstitutionalNoticesCheckbox({ id, checked, onCheckedChange, disabled, termsVersion }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="flex items-start gap-3">
        <Checkbox
          id={id}
          checked={checked}
          onCheckedChange={(value) => onCheckedChange(value === true)}
          disabled={disabled}
          className="mt-0.5"
        />
        <label htmlFor={id} className="text-size-13 font-semibold text-ink">
          Acepto recibir avisos institucionales (novedades, fechas y calendario), los{' '}
          <LegalLink type="TERMS" /> y la <LegalLink type="PRIVACY" />.
        </label>
      </div>
      {termsVersion && <p className="mt-1.5 pl-7 text-size-12 text-ink-soft">Versión {termsVersion}</p>}
    </div>
  );
}
