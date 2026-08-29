import { useReducer } from 'react';
import { Dialog, DialogContent, DialogHeader } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { useSolicitarCarrera, useInscribirmeCurso } from '@/hooks/useCatalogo';
import { formatMonto } from '@/lib/utils';

// State (patrón, ver spec técnica de Épica 7 Fase 3): transiciones explícitas
// en vez de un array de useState sueltos por paso. Carrera arranca en
// "cohorte" (tiene que elegir una antes de pagar); curso arranca directo en
// "pago" (no tiene cohortes, decisión ya tomada en Épica 8 Fase 2).
function pasoInicial(tipo) {
  return tipo === 'carrera' ? 'cohorte' : 'pago';
}

function reducer(state, action) {
  switch (action.type) {
    case 'ELEGIR_COHORTE':
      return { ...state, cohortId: action.cohortId, paso: 'pago' };
    case 'VOLVER_A_COHORTE':
      return { ...state, paso: 'cohorte', error: null };
    case 'ELEGIR_COMPROBANTE':
      return { ...state, comprobante: action.file, error: null };
    case 'ENVIANDO':
      return { ...state, enviando: true, error: null };
    case 'ERROR':
      return { ...state, enviando: false, error: action.message };
    case 'EXITO':
      return { ...state, enviando: false, paso: 'confirmacion', resultado: action.resultado };
    case 'RESET':
      return estadoInicial(action.tipo);
    default:
      return state;
  }
}

function estadoInicial(tipo) {
  return { paso: pasoInicial(tipo), cohortId: null, comprobante: null, enviando: false, error: null, resultado: null };
}

// tipo: 'carrera' | 'curso'. target: { id, name }. cohorts solo aplica a carrera.
export function WizardInscripcion({ tipo, target, cohorts, pricing, open, onOpenChange, onSuccess }) {
  const [state, dispatch] = useReducer(reducer, tipo, estadoInicial);
  const solicitarCarrera = useSolicitarCarrera();
  const inscribirmeCurso = useInscribirmeCurso();

  const cohorteElegida = cohorts?.find((c) => c.id === state.cohortId);
  const montoAPagar =
    tipo === 'carrera'
      ? (cohorteElegida?.ordenEnAnio === 2 ? pricing?.matriculaCohorte2 : pricing?.matriculaCohorte1)
      : pricing?.cuotaMensual;

  function cerrar() {
    onOpenChange(false);
    dispatch({ type: 'RESET', tipo });
  }

  async function confirmar() {
    if (!state.comprobante) {
      dispatch({ type: 'ERROR', message: 'Subí el comprobante de pago para continuar.' });
      return;
    }
    dispatch({ type: 'ENVIANDO' });
    try {
      const resultado =
        tipo === 'carrera'
          ? await solicitarCarrera.mutateAsync({ programId: target.id, cohortId: state.cohortId, comprobante: state.comprobante })
          : await inscribirmeCurso.mutateAsync({ courseId: target.id, comprobante: state.comprobante });
      dispatch({ type: 'EXITO', resultado });
      onSuccess?.(resultado);
    } catch (err) {
      dispatch({
        type: 'ERROR',
        message: err.message === 'La operación entra en conflicto con el estado actual.'
          ? 'Ya tenés una solicitud pendiente para esto.'
          : 'No pudimos completar la inscripción. Probá de nuevo.',
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(true) : cerrar())}>
      <DialogContent className="max-w-md rounded-xl border border-border bg-surface p-6 text-ink backdrop-blur-[28px] backdrop-saturate-[1.6]">
        <DialogHeader>
          <div className="font-heading text-lg font-bold text-ink">Inscribirme — {target.name}</div>
        </DialogHeader>

        {state.paso === 'cohorte' && (
          <div className="flex flex-col gap-3">
            <div className="text-[13px] text-ink-soft">Elegí la cohorte a la que te querés inscribir.</div>
            {cohorts?.length === 0 && (
              <div className="rounded-lg bg-warning-bg px-3 py-2 text-xs font-medium text-warning">
                Todavía no hay cohortes abiertas para esta carrera.
              </div>
            )}
            {cohorts?.map((cohort) => (
              <button
                key={cohort.id}
                type="button"
                onClick={() => dispatch({ type: 'ELEGIR_COHORTE', cohortId: cohort.id })}
                className="flex flex-col rounded-xl border border-border bg-surface px-4 py-3 text-left hover:bg-neutral-bg"
              >
                <span className="text-[13.5px] font-bold text-ink">{cohort.label ?? `Cohorte ${cohort.ordenEnAnio}`}</span>
                <span className="text-xs text-ink-faint">Inicio de cursada: {cohort.cursadaStart}</span>
                <span className="mt-1 text-xs font-semibold text-accent">
                  Matrícula: {formatMonto(cohort.ordenEnAnio === 2 ? pricing?.matriculaCohorte2 : pricing?.matriculaCohorte1) || 'A definir'}
                </span>
              </button>
            ))}
          </div>
        )}

        {state.paso === 'pago' && (
          <div className="flex flex-col gap-4">
            {tipo === 'carrera' && cohorteElegida && (
              <div className="text-xs text-ink-faint">Cohorte elegida: {cohorteElegida.label ?? `Cohorte ${cohorteElegida.ordenEnAnio}`}</div>
            )}
            <div className="rounded-xl bg-neutral-bg px-4 py-3">
              <div className="text-xs text-ink-faint">Monto a pagar</div>
              <div className="font-heading text-xl font-bold text-ink">{formatMonto(montoAPagar) || 'A definir'}</div>
              <div className="mt-1 text-xs text-ink-faint">
                {tipo === 'carrera' ? 'Matrícula' : 'Primera cuota'} — transferí y subí el comprobante abajo.
              </div>
            </div>

            {state.error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{state.error}</div>}

            <label className="flex cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border border-dashed border-border px-4 py-6 text-center hover:bg-neutral-bg">
              <span className="text-[13px] font-semibold text-ink">
                {state.comprobante ? state.comprobante.name : 'Subir comprobante de pago'}
              </span>
              <span className="text-xs text-ink-faint">PDF, JPG o PNG</span>
              <input
                type="file"
                className="hidden"
                onChange={(e) => dispatch({ type: 'ELEGIR_COMPROBANTE', file: e.target.files?.[0] })}
              />
            </label>

            <div className="flex justify-between gap-3">
              {tipo === 'carrera' ? (
                <Button variant="outline" onClick={() => dispatch({ type: 'VOLVER_A_COHORTE' })} className="rounded-xl">
                  ← Volver
                </Button>
              ) : (
                <span />
              )}
              <Button
                onClick={confirmar}
                disabled={state.enviando}
                className="rounded-xl bg-gradient-to-br from-accent to-accent-2 font-semibold text-white"
              >
                {state.enviando ? 'Enviando…' : 'Confirmar inscripción'}
              </Button>
            </div>
          </div>
        )}

        {state.paso === 'confirmacion' && (
          <div className="flex flex-col items-center gap-3 py-4 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-success-bg text-2xl text-success">✓</div>
            <div className="font-heading text-base font-bold text-ink">Solicitud enviada</div>
            <div className="text-[13px] text-ink-soft">
              {tipo === 'carrera'
                ? 'Tu comprobante queda en revisión. Cuando lo aprueben, vas a poder subir tu documentación y quedar inscripto.'
                : 'Tu comprobante queda en revisión. Cuando lo aprueben, vas a quedar inscripto en el curso.'}
            </div>
            <Button onClick={cerrar} className="mt-2 rounded-xl bg-gradient-to-br from-accent to-accent-2 font-semibold text-white">
              Entendido
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
