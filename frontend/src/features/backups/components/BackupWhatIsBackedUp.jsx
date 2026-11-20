import { Check, Minus } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { TIER_LABELS } from '@/features/backups/lib/backupFormat';

const INCLUDED = [
  'Todas las tablas de la base: alumnos, cursos, materias, notas, asistencia, pagos e historial completo',
  'Los certificados emitidos',
  'Los archivos subidos',
];
const EXCLUDED = ['La configuración del servidor', 'Las claves'];

function BulletList({ items, icon: Icon, iconClass }) {
  return (
    <ul className="flex flex-col gap-2 px-5 pb-4 text-size-14">
      {items.map((item) => (
        <li key={item} className="flex items-start gap-2.5">
          <Icon className={`mt-0.5 size-4 shrink-0 ${iconClass}`} aria-hidden="true" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}

function keptLabel(tier) {
  if (tier.tier === 'PRE_RELEASE') return 'Fuera de la rotación';
  return `${tier.kept} ${tier.kept === 1 ? 'copia' : 'copias'}`;
}

export function BackupWhatIsBackedUp({ tiers, diskPath, isPlatformAdmin }) {
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <GlassCard className="p-0">
          <h2 className="px-5 py-4 font-heading text-size-16 font-bold text-ink">Entra</h2>
          <BulletList items={INCLUDED} icon={Check} iconClass="text-pill-success" />
        </GlassCard>
        <GlassCard className="p-0">
          <h2 className="px-5 py-4 font-heading text-size-16 font-bold text-ink">No entra</h2>
          <BulletList items={EXCLUDED} icon={Minus} iconClass="text-ink-soft" />
          <p className="px-5 pb-4 text-size-12-5 text-ink-soft">Se guardan aparte, fuera de este disco.</p>
        </GlassCard>
      </div>
      <GlassCard className="p-0">
        <h2 className="px-5 py-4 font-heading text-size-16 font-bold text-ink">Cuántas se conservan</h2>
        <dl className="grid grid-cols-[minmax(0,1fr)_auto] border-t border-border text-size-14">
          {tiers.map((tier) => (
            <div key={tier.tier} className="col-span-2 grid grid-cols-subgrid border-b border-border px-5 py-3 last:border-b-0">
              <dt>{TIER_LABELS[tier.tier]}</dt>
              <dd className="font-semibold tabular-nums">{keptLabel(tier)}</dd>
            </div>
          ))}
        </dl>
        {isPlatformAdmin && (
          <div className="flex flex-col gap-1.5 border-t border-border px-5 py-3.5 text-size-13">
            {diskPath && (
              <span>
                Disco: <code className="rounded-sm bg-neutral-bg px-1.5 font-mono text-size-12 break-all">{diskPath}</code> (variable{' '}
                <code className="rounded-sm bg-neutral-bg px-1.5 font-mono text-size-12">BACKUP_DIR</code>)
              </span>
            )}
            <span>
              Cifrado con <code className="rounded-sm bg-neutral-bg px-1.5 font-mono text-size-12">age</code> (clave pública en{' '}
              <code className="rounded-sm bg-neutral-bg px-1.5 font-mono text-size-12">BACKUP_AGE_RECIPIENT</code>)
            </span>
          </div>
        )}
      </GlassCard>
    </div>
  );
}
