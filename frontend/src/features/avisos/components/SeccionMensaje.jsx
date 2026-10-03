import { Info } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CATEGORIA_AVISO_LABEL, hintDePrivacidad } from '@/lib/avisos';

function Contador({ actual, max }) {
  const cerca = actual >= max * 0.9;
  return (
    <span className={`text-[11px] tabular-nums ${cerca ? 'font-bold text-warning' : 'text-ink-faint'}`}>
      {actual}/{max}
    </span>
  );
}

export function SeccionMensaje({ valores, onChange, mostrarHintPrivacidad, alcanzados, programado, children }) {
  return (
    <GlassCard className="flex flex-col gap-4 p-5">
      <div className="flex flex-wrap items-end gap-3.5">
        <div className="flex min-w-[220px] flex-1 flex-col gap-1.5">
          <label className="text-[13px] font-semibold text-ink" htmlFor="categoria">
            Categoría
          </label>
          <Select value={valores.category} onValueChange={(category) => onChange({ ...valores, category })}>
            <SelectTrigger id="categoria">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(CATEGORIA_AVISO_LABEL).map(([key, label]) => (
                <SelectItem key={key} value={key}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <button
          type="button"
          aria-pressed={valores.urgent}
          onClick={() => onChange({ ...valores, urgent: !valores.urgent })}
          className={`rounded-xl border px-3.5 py-2 text-[12.5px] font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand ${
            valores.urgent
              ? 'border-transparent bg-gradient-to-br from-brand to-accent-2 text-white'
              : 'border-border text-ink-soft hover:border-brand hover:text-ink'
          }`}
        >
          Marcar como urgente
        </button>
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <label className="text-[13px] font-semibold text-ink" htmlFor="titulo">
            Título
          </label>
          <Contador actual={valores.title.length} max={100} />
        </div>
        <Input
          id="titulo"
          maxLength={100}
          value={valores.title}
          onChange={(e) => onChange({ ...valores, title: e.target.value })}
          placeholder="Ej: Recuperatorio del 2° parcial"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <label className="text-[13px] font-semibold text-ink" htmlFor="mensaje">
            Mensaje
          </label>
          <Contador actual={valores.body.length} max={1000} />
        </div>
        <Textarea
          id="mensaje"
          maxLength={1000}
          rows={6}
          value={valores.body}
          onChange={(e) => onChange({ ...valores, body: e.target.value })}
          placeholder="Escribí el aviso…"
        />
      </div>

      {mostrarHintPrivacidad && (
        <div className="nota-fila flex items-start gap-2.5 py-0.5 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-2" aria-hidden="true" />
          <span>{hintDePrivacidad({ alcanzados, programado })}</span>
        </div>
      )}

      {children}
    </GlassCard>
  );
}
