import { useEffect, useRef, useState } from 'react';
import { ChevronRight, Info } from 'lucide-react';
import { StatusPill } from '@/components/StatusPill';
import { Button } from '@/components/ui/button';
import {
  CATEGORIA_AVISO_LABEL,
  esAudienciaCompleta,
  estadoDeVigencia,
  formatearFechaCorta,
  formatearFechaHora,
} from '@/lib/avisos';

function CeldaFecha({ principal, secundario }) {
  return (
    <td className="whitespace-nowrap px-3 py-[11px] text-[12px] text-ink-faint">
      {principal ?? <span className="text-ink-faint">—</span>}
      {secundario && <small className="block text-[10.5px] text-ink-faint opacity-85">{secundario}</small>}
    </td>
  );
}

function useAnchoDelScroller(scrollerRef, activo) {
  const [ancho, setAncho] = useState(null);

  useEffect(() => {
    const scroller = scrollerRef?.current;
    if (!activo || !scroller || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entrada]) => setAncho(entrada.contentRect.width));
    observer.observe(scroller);
    return () => observer.disconnect();
  }, [scrollerRef, activo]);

  return ancho;
}

export function FilaAviso({ aviso, scrollerRef, soloLectura, onEditar, onEliminar, onVerDestinatarios }) {
  const [abierto, setAbierto] = useState(false);
  const detalleRef = useRef(null);
  const anchoVisible = useAnchoDelScroller(scrollerRef, abierto);
  const vigencia = estadoDeVigencia(aviso);
  const audienciaCompleta = esAudienciaCompleta(aviso);

  return (
    <>
      <tr className="fila-aviso cursor-pointer transition-colors" data-abierto={abierto} onClick={() => setAbierto((v) => !v)}>
        <td className="col-aviso px-3 py-[11px]">
          <span className="flex items-center gap-2">
            <ChevronRight
              className={`h-3.5 w-3.5 shrink-0 text-ink-faint transition-transform ${abierto ? 'rotate-90' : ''}`}
              aria-hidden="true"
            />
            <span className="min-w-0 text-balance font-heading text-[14px] font-medium leading-snug text-ink">
              {aviso.title}
              {aviso.urgent && (
                <StatusPill tone="danger" className="ml-1.5 align-[2px] px-2 py-0.5 text-[10px]">
                  Urgente
                </StatusPill>
              )}
            </span>
          </span>
        </td>
        <td className="whitespace-nowrap px-3 py-[11px] text-[12.5px] text-ink-soft">
          {CATEGORIA_AVISO_LABEL[aviso.category] ?? aviso.category}
        </td>
        <td className="px-3 py-[11px] text-[12.5px] text-ink-soft">
          <button
            type="button"
            className="cursor-pointer rounded-full transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
            aria-label={`Ver destinatarios: ${aviso.audienceLabel}`}
            onClick={(e) => {
              e.stopPropagation();
              onVerDestinatarios(aviso);
            }}
          >
            <StatusPill tone={audienciaCompleta ? 'neutral' : 'accent'} className="whitespace-nowrap">
              {aviso.audienceLabel}
            </StatusPill>
          </button>
        </td>
        <td className="px-3 py-[11px] text-[12.5px]">
          <StatusPill tone={vigencia.tone}>{vigencia.label}</StatusPill>
        </td>
        <CeldaFecha principal={formatearFechaCorta(aviso.createdAt)} />
        <CeldaFecha
          principal={aviso.status === 'SCHEDULED' ? formatearFechaCorta(aviso.scheduledFor) : formatearFechaCorta(aviso.sentAt)}
          secundario={aviso.status === 'SCHEDULED' ? 'programado' : null}
        />
        <CeldaFecha principal={formatearFechaCorta(aviso.expiresAt)} />
      </tr>

      {abierto && (
        <tr className="detalle-aviso">
          <td colSpan={7} className="px-4 pb-4 pt-0">
            <div
              ref={detalleRef}
              className="sticky left-4 flex max-w-[78ch] flex-col gap-3"
              style={anchoVisible ? { width: `${anchoVisible - 32}px` } : undefined}
            >
              {aviso.status === 'SCHEDULED' && (
                <div className="nota-fila flex items-start gap-2.5 py-0.5 pl-3 text-[12.5px] leading-relaxed text-ink-soft">
                  <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent-2" aria-hidden="true" />
                  <span>
                    Todavía no se envió. El grupo de destinatarios se calcula en el momento del envío, así que puede no
                    coincidir con el de hoy.
                  </span>
                </div>
              )}

              <div>
                <div className="text-[10.5px] font-bold uppercase tracking-[0.04em] text-ink-faint">Mensaje</div>
                <p className="mt-1 whitespace-pre-line text-[13px] leading-relaxed text-ink-soft">{aviso.body}</p>
              </div>

              {!audienciaCompleta && (
                <button
                  type="button"
                  className="w-fit text-left text-[12.5px] font-semibold text-brand hover:underline"
                  onClick={() => onVerDestinatarios(aviso)}
                >
                  Ver quiénes son los destinatarios
                </button>
              )}

              {aviso.updatedAt && (
                <div className="text-[11.5px] text-ink-faint">Editado el {formatearFechaHora(aviso.updatedAt)}</div>
              )}

              {!soloLectura && (
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" size="sm" onClick={() => onEditar(aviso)}>
                    Editar
                  </Button>
                  <Button variant="destructive" size="sm" onClick={() => onEliminar(aviso)}>
                    Eliminar
                  </Button>
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}
