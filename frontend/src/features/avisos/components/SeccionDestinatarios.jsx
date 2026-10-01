import { Info, TriangleAlert } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { agruparSegmentos } from '@/lib/avisos';
import { ParametrosDeSegmento } from './ParametrosDeSegmento';

export function SeccionDestinatarios({ segmentos, cargandoSegmentos, spec, onChange, preview, recalculando, onVerDestinatarios }) {
  const grupos = agruparSegmentos(segmentos);
  const segmento = (segmentos ?? []).find((s) => s.key === spec.key);
  const alcanzados = preview?.count ?? 0;
  const total = preview?.totalInSection ?? 0;
  const nadie = !recalculando && preview && alcanzados === 0;

  function elegirSegmento(key) {
    onChange({ key, instances: [], quantifier: 'ANY', threshold: key === 'ABSENCES' ? 3 : null });
  }

  if (cargandoSegmentos) {
    return (
      <GlassCard className="flex flex-col gap-4 p-5">
        <Skeleton className="h-10" />
        <Skeleton className="h-20" />
      </GlassCard>
    );
  }

  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <div className="flex flex-col gap-1.5">
        <label className="text-[13px] font-semibold text-ink" htmlFor="segmento">
          ¿A quiénes les llega?
        </label>
        <Select value={spec.key} onValueChange={elegirSegmento}>
          <SelectTrigger id="segmento">
            <SelectValue placeholder="Elegí un grupo" />
          </SelectTrigger>
          <SelectContent>
            {grupos.map((grupo) => (
              <SelectGroup key={grupo.titulo}>
                <SelectLabel>{grupo.titulo}</SelectLabel>
                {grupo.opciones.map((opcion) => (
                  <SelectItem key={opcion.key} value={opcion.key}>
                    {opcion.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            ))}
          </SelectContent>
        </Select>
      </div>

      <ParametrosDeSegmento segmento={segmento} spec={spec} onChange={onChange} />

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-accent-soft/60 px-4 py-3.5">
        <div>
          <div
            className={`font-heading text-[26px] font-bold leading-none tabular-nums text-ink transition-opacity ${
              recalculando ? 'opacity-40' : ''
            }`}
          >
            {alcanzados}
          </div>
          <div className="mt-1 text-[12.5px] text-ink-soft">
            {alcanzados === 1 ? `de ${total} alumnos — es uno solo` : `de ${total} alumnos de la comisión`}
          </div>
        </div>
        <Button type="button" variant="outline" disabled={alcanzados === 0} onClick={onVerDestinatarios}>
          Ver quiénes son
        </Button>
      </div>

      {preview?.label && spec.key !== 'ALL' && (
        <div className="nota-fila flex items-start gap-2.5 py-0.5 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-2" aria-hidden="true" />
          <span>
            Se le envía a quienes <span className="font-bold text-ink">{preview.label.toLowerCase()}</span>.
          </span>
        </div>
      )}

      {preview?.hasUngradedInstances && (
        <div className="nota-fila flex items-start gap-2.5 border-l-warning py-0.5 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
          <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning" aria-hidden="true" />
          <span>
            <span className="font-bold text-ink">Hay instancias sin corregir.</span> Los alumnos sin nota cargada no
            entran en este grupo, así que el conteo puede crecer cuando termines de corregir.
          </span>
        </div>
      )}

      {nadie && (
        <div className="nota-fila flex items-start gap-2.5 border-l-danger py-0.5 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
          <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0 text-danger" aria-hidden="true" />
          <span>
            <span className="font-bold text-ink">Ningún alumno cumple esta condición hoy.</span> Si lo mandás igual no
            lo recibe nadie, así que no se puede publicar.
          </span>
        </div>
      )}
    </GlassCard>
  );
}
