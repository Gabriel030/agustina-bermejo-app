import { DataTable } from '@/components/table/DataTable';
import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { TIER_LABELS, VERIFICATION_PILLS, formatBackupMoment, formatBytes } from '@/features/backups/lib/backupFormat';

function buildColumns(showFileNames) {
  return [
    {
      id: 'tier',
      header: 'Nivel',
      accessorFn: (tier) => TIER_LABELS[tier.tier],
      cell: ({ row }) => (
        <span>
          <span className="font-semibold">{TIER_LABELS[row.original.tier]}</span>
          {row.original.label && <span className="block text-size-12 text-ink-soft">{row.original.label}</span>}
        </span>
      ),
      meta: { cardTitle: true },
      enableSorting: false,
    },
    {
      id: 'takenAt',
      header: 'Fecha y hora',
      accessorFn: (tier) => tier.takenAt,
      cell: ({ row }) => (
        <span className="tabular-nums">
          {formatBackupMoment(row.original.takenAt)}
          {showFileNames &&
            row.original.fileNames?.map((fileName) => (
              <span key={fileName} className="mt-0.5 block font-mono text-size-11-5 break-all text-ink-soft">
                {fileName}
              </span>
            ))}
        </span>
      ),
      enableSorting: false,
    },
    {
      id: 'size',
      header: 'Tamaño (base + archivos)',
      accessorFn: (tier) => tier.dumpSizeBytes + tier.filesSizeBytes,
      cell: ({ row }) => <span className="tabular-nums">{`${formatBytes(row.original.dumpSizeBytes)} + ${formatBytes(row.original.filesSizeBytes)}`}</span>,
      meta: { align: 'right' },
      enableSorting: false,
    },
    {
      id: 'verification',
      header: 'Verificación',
      accessorFn: (tier) => tier.verification,
      cell: ({ row }) => {
        const pill = VERIFICATION_PILLS[row.original.verification];
        return <StatusPill tone={pill.tone}>{pill.label}</StatusPill>;
      },
      enableSorting: false,
    },
    {
      id: 'kept',
      header: 'Se conservan',
      accessorFn: (tier) => tier.kept,
      cell: ({ row }) => <span className="tabular-nums">{row.original.kept}</span>,
      meta: { align: 'right' },
      enableSorting: false,
    },
  ];
}

export function BackupTiersTable({ tiers, isPlatformAdmin }) {
  const hasUnverified = tiers.some((tier) => tier.verification === 'UNVERIFIED');
  return (
    <section aria-labelledby="backup-tiers-heading">
      <GlassCard className="p-0">
        <h2 id="backup-tiers-heading" className="px-5 pt-4 pb-2 font-heading text-size-16 font-bold text-ink">
          Último backup por nivel
        </h2>
        <DataTable columns={buildColumns(isPlatformAdmin)} data={tiers} getRowId={(tier) => tier.tier} minWidth={720} />
        {hasUnverified && (
          <p className="px-5 pt-1 pb-3 text-size-12-5 text-ink-soft">
            <b className="text-ink">Sin verificar:</b> la copia existe, pero todavía no se comprobó que esté completa.
          </p>
        )}
      </GlassCard>
    </section>
  );
}
