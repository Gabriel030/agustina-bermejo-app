import { Button } from '@/components/ui/button';
import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import {
  construirEventosDelCalendario,
  mesesDelRango,
  indiceDelMesActual,
  construirGrillaDeMes,
  NOMBRES_DIA_SEMANA,
  eventosDelDia,
  esFeriado,
} from '@/lib/calendario';

const TONO_BG = { success: 'bg-success text-on-accent', warning: 'bg-warning text-on-accent', accent: 'bg-brand text-on-accent', accent2: 'bg-accent-2 text-on-accent' };
const MESES_POR_PAGINA = 5;

function DiaCelda({ dia, eventos, feriado }) {
  if (dia === null) return <div />;
  const evento = eventos[0];
  if (evento) {
    return (
      <div
        title={eventos.map((e) => e.etiqueta).join(', ')}
        className={`flex aspect-square items-center justify-center rounded-full text-size-10 font-bold ${TONO_BG[evento.tono]}`}
      >
        {dia}
      </div>
    );
  }
  if (feriado) {
    return (
      <div title="Feriado" className="flex aspect-square items-center justify-center rounded bg-neutral-bg text-size-10 text-ink-faint line-through">
        {dia}
      </div>
    );
  }
  return <div className="flex aspect-square items-center justify-center text-size-10 text-ink-faint">{dia}</div>;
}

export function CalendarioConfiguracion({ config, form, feriados }) {
  const eventos = useMemo(() => construirEventosDelCalendario({ ...config, ...form }), [config, form]);
  const meses = useMemo(() => mesesDelRango(config.termStartDate, config.termEndDate), [config.termStartDate, config.termEndDate]);
  const [pagina, setPagina] = useState(() => Math.floor(indiceDelMesActual(meses) / MESES_POR_PAGINA));

  if (meses.length === 0) {
    return <div className="text-sm text-ink-faint">No hay fechas de cuatrimestre cargadas todavía.</div>;
  }

  const totalPaginas = Math.ceil(meses.length / MESES_POR_PAGINA);
  const paginaActual = Math.min(pagina, totalPaginas - 1);
  const mesesVisibles = meses.slice(paginaActual * MESES_POR_PAGINA, paginaActual * MESES_POR_PAGINA + MESES_POR_PAGINA);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-4 text-size-10-5 font-semibold text-ink-faint">
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-success" /> Apertura/cierre
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-warning" /> TP
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-brand" /> Parcial
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-accent-2" /> Llamado a final
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded bg-neutral-bg" /> Feriado
          </span>
        </div>
        {totalPaginas > 1 && (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon-sm"
              type="button"
              onClick={() => setPagina((p) => Math.max(0, p - 1))}
              disabled={paginaActual === 0}
              className="text-ink-faint disabled:opacity-40">
              <ChevronLeft className="h-3.5 w-3.5" />
            </Button>
            <span className="text-size-11 font-semibold text-ink-faint">
              {mesesVisibles[0]?.anio} · página {paginaActual + 1}/{totalPaginas}
            </span>
            <Button
              variant="outline"
              size="icon-sm"
              type="button"
              onClick={() => setPagina((p) => Math.min(totalPaginas - 1, p + 1))}
              disabled={paginaActual === totalPaginas - 1}
              className="text-ink-faint disabled:opacity-40">
              <ChevronRight className="h-3.5 w-3.5" />
            </Button>
          </div>
        )}
      </div>
      <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5">
        {mesesVisibles.map(({ anio, mes, clave }) => {
          const grilla = construirGrillaDeMes(anio, mes);
          return (
            <div key={clave} className="rounded-2xl border border-border bg-surface p-2.5">
              <div className="mb-1.5 text-center text-size-11 font-bold text-ink">{grilla.nombre}</div>
              <div className="grid grid-cols-7 gap-0.5 text-center text-[7.5px] font-bold text-ink-faint">
                {NOMBRES_DIA_SEMANA.map((d) => (
                  <div key={d}>{d}</div>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-0.5">
                {grilla.celdas.map((dia, i) => (
                  <DiaCelda
                    key={i}
                    dia={dia}
                    eventos={dia ? eventosDelDia(eventos, anio, mes, dia) : []}
                    feriado={dia ? esFeriado(feriados, anio, mes, dia) : false}
                  />
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
