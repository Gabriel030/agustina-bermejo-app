import { Lock } from 'lucide-react';
import { detailRows } from '@/features/graduation/certificates/lib/certificateView';
import { cn } from '@/lib/utils';

export function CertificateDetails({ certificate }) {
  return (
    <>
      <dl className="grid grid-cols-[minmax(96px,auto)_1fr] gap-x-4 gap-y-2 px-5 py-4 text-size-14 sm:px-[22px]">
        {detailRows(certificate).map((row) => (
          <div key={row.label} className="col-span-2 grid grid-cols-subgrid">
            <dt className="text-ink-soft">{row.label}</dt>
            <dd className={cn('font-semibold [overflow-wrap:anywhere] text-ink', row.masked && 'tracking-[0.04em] tabular-nums')}>
              {row.value}
            </dd>
          </div>
        ))}
      </dl>
      <p className="flex items-start gap-2 border-t border-border px-5 py-3 text-size-12-5 text-ink-faint sm:px-[22px]">
        <Lock className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
        <span>
          <b>Privacidad.</b> Por privacidad, esta página no muestra notas ni materias, y oculta parte de los datos personales.
        </span>
      </p>
    </>
  );
}
