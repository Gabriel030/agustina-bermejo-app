import { detailRows, documentTitle, formatDayAndTime } from '@/features/graduation/certificates/lib/certificateView';
import { formatCertificateCode } from '@/features/graduation/certificates/lib/certificateCode';

export function CertificateDetails({ certificate, code, consultedAt }) {
  return (
    <>
      <h2 className="px-4 pt-4 pb-1 text-xs font-bold tracking-[0.08em] text-ink-faint uppercase sm:px-5">
        {documentTitle(certificate)}
      </h2>
      <p className="px-4 pb-3.5 font-heading text-size-22 font-bold text-balance text-ink sm:px-5">
        {certificate.holderFullName}
      </p>
      <dl className="border-t border-border">
        {detailRows(certificate).map((row) => (
          <div
            key={row.label}
            className="grid gap-0.5 border-t border-border px-4 py-3 first:border-t-0 sm:grid-cols-[150px_minmax(0,1fr)] sm:gap-3 sm:px-5"
          >
            <dt className="text-size-13-5 font-semibold text-ink-soft">{row.label}</dt>
            <dd className="font-semibold tabular-nums [overflow-wrap:anywhere] text-ink">{row.value}</dd>
          </div>
        ))}
      </dl>
      <div className="flex flex-wrap justify-between gap-3 border-t border-border px-4 py-3.5 text-size-13 text-ink-soft sm:px-5">
        <span>
          Código <b className="tracking-[0.04em] tabular-nums">{formatCertificateCode(code)}</b>
        </span>
        <span>Consultado el {formatDayAndTime(consultedAt)}</span>
      </div>
    </>
  );
}
