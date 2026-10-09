import { useReducer, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CircleCheck } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { PageContainer } from '@/components/PageContainer';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Dialog, DialogContent, DialogHeader, DialogFooter } from '@/components/ui/dialog';
import { Stepper } from '@/components/Stepper';
import { useAuth } from '@/context/AuthContext';
import {
  useSolicitarCarrera,
  useInscribirmeCurso,
  useActualizarPerfilPropio,
  useAgregarDocumentoSolicitudCarrera,
} from '@/features/enrollment/hooks/useCatalogo';
import { BankAccountDetails } from '@/components/catalogo/BankAccountDetails';
import { formatMoney, cn } from '@/lib/utils';

// Tipos de documento requeridos para carrera (Épica 7 Fase 3, UX del wizard) —
// los cursos no piden documentación (ver TabMisSolicitudes.jsx). El input de
// archivo lleva `capture="environment"` para que en la app empaquetada
// (Capacitor) se pueda sacar la foto directo desde la cámara, no solo elegir
// de galería — pedido explícito del usuario.
const DOCUMENTOS_CARRERA = [
  { tipo: 'DNI_FRENTE', label: 'DNI — frente' },
  { tipo: 'DNI_DORSO', label: 'DNI — dorso' },
  { tipo: 'ANALITICO_SECUNDARIO', label: 'Analítico del secundario' },
];

const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const MENSAJES_ERROR_INSCRIPCION = {
  ALREADY_HAS_PENDING_APPLICATION: 'Ya tenés una solicitud pendiente para esto.',
  PENDING_COURSE_ENROLLMENT_APPLICATION: 'Ya tenés una solicitud pendiente para esto.',
  ALREADY_ENROLLED_IN_COURSE: 'Ya tenés una inscripción activa en este curso.',
  ALREADY_ENROLLED_IN_PROGRAM: 'Ya tenés una inscripción activa en esta carrera.',
  COHORT_ADMISSION_WINDOW_CLOSED: 'La inscripción a esta cohorte está cerrada.',
  ADMISSION_WINDOW_CLOSED: 'La inscripción a este curso está cerrada.',
  STUDENT_NOT_CONFIRMED: 'Tu cuenta todavía no está habilitada para inscribirte. Consultá en Secretaría.',
  DNI_REQUIRED: 'Cargá tu DNI para poder enviar la solicitud.',
  DNI_ALREADY_EXISTS: 'Ese DNI ya está registrado en otra cuenta. Revisalo en tus datos personales.',
  STUDENT_IN_DEBT: 'Tenés deuda pendiente. Regularizala antes de inscribirte.',
  PENDING_DEBT: 'Tenés deuda pendiente. Regularizala antes de inscribirte.',
};

function pasosDe(tipo) {
  return tipo === 'carrera'
    ? [
        { key: 'cohorte', label: 'Cohorte' },
        { key: 'documentacion', label: 'Documentación' },
      ]
    : [
        { key: 'documentacion', label: 'Documentación' },
        { key: 'pago', label: 'Pago' },
      ];
}

function estadoInicial({ tipo, usuario }) {
  const datos = usuario ?? {};
  return {
    paso: pasosDe(tipo)[0].key,
    cohortId: null,
    email: datos.email ?? '',
    dni: datos.dni ?? '',
    street: datos.street ?? '',
    number: datos.number ?? '',
    floor: datos.floor ?? '',
    city: datos.city ?? '',
    state: datos.state ?? '',
    postalCode: datos.postalCode ?? '',
    country: datos.country ?? '',
    documentos: {},
    comprobante: null,
    visitados: [pasosDe(tipo)[0].key],
    enviando: false,
    error: null,
    exito: false,
  };
}

function marcarVisitado(state, paso) {
  return state.visitados.includes(paso) ? state.visitados : [...state.visitados, paso];
}

// El estado vive en un único useReducer para todo el wizard (no un useState
// por paso) — es lo que permite "ir para atrás y para adelante" sin perder lo
// ya cargado (pedido explícito): cambiar `paso` nunca toca los demás campos.
function reducer(state, action) {
  switch (action.type) {
    case 'SET_CAMPO':
      return { ...state, [action.campo]: action.valor };
    case 'ELEGIR_COHORTE':
      return { ...state, cohortId: action.cohortId, paso: 'documentacion', visitados: marcarVisitado(state, 'documentacion') };
    case 'SET_DOCUMENTO':
      return { ...state, documentos: { ...state.documentos, [action.tipo]: action.file } };
    case 'IR_A_PASO':
      return { ...state, paso: action.paso, error: null, visitados: marcarVisitado(state, action.paso) };
    case 'ELEGIR_COMPROBANTE':
      return { ...state, comprobante: action.file, error: null };
    case 'ENVIANDO':
      return { ...state, enviando: true, error: null };
    case 'ERROR':
      return { ...state, enviando: false, error: action.message };
    case 'EXITO':
      return { ...state, enviando: false, exito: true };
    default:
      return state;
  }
}

// tipo: 'carrera' | 'curso'. target: { id, name }. cohorts/pricing según tipo.
export function InscripcionWizard({ tipo, target, cohorts, pricing, volverA }) {
  const navigate = useNavigate();
  const { usuario, refrescarPerfil } = useAuth();
  const [state, dispatch] = useReducer(reducer, { tipo, usuario }, estadoInicial);
  const [modalConfirmarAbierto, setModalConfirmarAbierto] = useState(false);
  const pasos = pasosDe(tipo);

  const solicitarCarrera = useSolicitarCarrera();
  const inscribirmeCurso = useInscribirmeCurso();
  const actualizarPerfil = useActualizarPerfilPropio();
  const agregarDocumento = useAgregarDocumentoSolicitudCarrera();

  const cohorteElegida = cohorts?.find((c) => c.id === state.cohortId);
  const montoAPagar =
    tipo === 'carrera'
      ? cohorteElegida?.ordenEnAnio === 2
        ? pricing?.matriculaCohorte2
        : pricing?.matriculaCohorte1
      : pricing?.cuotaMensual;

  function datosPersonalesValidos() {
    return (
      EMAIL_VALIDO.test(state.email.trim()) &&
      state.dni.trim() &&
      state.street.trim() &&
      state.number.trim() &&
      state.city.trim()
    );
  }

  function documentacionCompleta() {
    if (!datosPersonalesValidos()) return false;
    if (tipo === 'curso') return true;
    return DOCUMENTOS_CARRERA.every((d) => state.documentos[d.tipo]);
  }

  // "no podemos subir una solicitud incompleta" — se revalida acá, no solo al
  // avanzar de paso, para que el botón de confirmar nunca se habilite con
  // datos a medio cargar (ej. volviendo atrás y borrando algo).
  function solicitudCompleta() {
    return documentacionCompleta() && (tipo === 'carrera' || !!state.comprobante);
  }

  async function confirmarSolicitud() {
    setModalConfirmarAbierto(false);
    dispatch({ type: 'ENVIANDO' });
    try {
      const datosCambiaron =
        state.email !== (usuario?.email ?? '') ||
        state.dni !== (usuario?.dni ?? '') ||
        state.street !== (usuario?.street ?? '') ||
        state.number !== (usuario?.number ?? '') ||
        state.floor !== (usuario?.floor ?? '') ||
        state.city !== (usuario?.city ?? '') ||
        state.state !== (usuario?.state ?? '') ||
        state.postalCode !== (usuario?.postalCode ?? '') ||
        state.country !== (usuario?.country ?? '');
      if (datosCambiaron) {
        await actualizarPerfil.mutateAsync({
          email: state.email,
          dni: state.dni,
          street: state.street,
          number: state.number,
          floor: state.floor || null,
          city: state.city,
          state: state.state || null,
          postalCode: state.postalCode || null,
          country: state.country || null,
        });
        await refrescarPerfil();
      }

      const resultado =
        tipo === 'carrera'
          ? await solicitarCarrera.mutateAsync({ programId: target.id, cohortId: state.cohortId })
          : await inscribirmeCurso.mutateAsync({ courseId: target.id, comprobante: state.comprobante });

      if (tipo === 'carrera') {
        for (const doc of DOCUMENTOS_CARRERA) {
          await agregarDocumento.mutateAsync({ applicationId: resultado.id, type: doc.tipo, file: state.documentos[doc.tipo] });
        }
      }

      dispatch({ type: 'EXITO' });
    } catch (err) {
      dispatch({
        type: 'ERROR',
        message: MENSAJES_ERROR_INSCRIPCION[err.code] ?? 'No pudimos completar la inscripción. Probá de nuevo.',
      });
    }
  }

  if (state.exito) {
    return (
      <PageContainer className="mx-auto items-center justify-center py-16 text-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-success-bg text-success">
          <CircleCheck size={36} />
        </div>
        <div className="font-heading text-xl font-bold text-ink">Solicitud enviada</div>
        <div className="max-w-[420px] text-[13.5px] text-ink-soft">
          {tipo === 'carrera'
            ? `Tu solicitud para ${target.name} quedó en revisión y no se te cobró nada. Si la aprueban, te vamos a emitir la factura de la matrícula de ingreso${formatMoney(montoAPagar) ? ` (${formatMoney(montoAPagar)})` : ''} y vas a tener un plazo para pagarla: recién con ese pago queda confirmada tu inscripción. Te avisamos cuando haya novedades.`
            : 'Tu comprobante queda en revisión. Cuando lo aprueben, vas a quedar inscripto en el curso.'}
        </div>
        <Button onClick={() => navigate('/catalogo?tab=mis-solicitudes')} variant="gradient" className="mt-2 px-6">
          Ir a mis solicitudes
        </Button>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <button
        type="button"
        onClick={() => navigate(volverA)}
        className="flex w-fit items-center gap-1.5 text-[13px] font-semibold text-ink-soft hover:text-ink"
      >
        ← {target.name}
      </button>

      <div>
        <div className="font-heading text-xl font-bold tracking-tight text-ink">Inscribirme</div>
        <div className="mt-1 text-sm text-ink-faint">{target.name}</div>
      </div>

      <Stepper
        pasos={pasos}
        pasoActual={state.paso}
        pasosVisitados={state.visitados}
        onStepClick={(paso) => dispatch({ type: 'IR_A_PASO', paso })}
      />

      {state.paso === 'cohorte' && (
        <GlassCard className="flex flex-col gap-3 p-5">
          <div className="text-[13px] text-ink-soft">Elegí la cohorte a la que te querés inscribir.</div>
          {tipo === 'carrera' && (
            <div className="text-xs text-ink-faint">
              No se te cobra nada al enviar la solicitud. Si la aprueban, vas a recibir la factura de la matrícula y vas a tener un plazo para pagarla.
            </div>
          )}
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
              className={cn(
                'flex flex-col rounded-xl border px-4 py-3 text-left transition-colors hover:bg-neutral-bg',
                cohort.id === state.cohortId ? 'border-brand bg-accent-soft' : 'border-border bg-surface',
              )}
            >
              <span className="text-[13.5px] font-bold text-ink">{cohort.label ?? `Cohorte ${cohort.ordenEnAnio}`}</span>
              <span className="text-xs text-ink-faint">Inicio de cursada: {cohort.cursadaStart}</span>
              <span className="mt-1 text-xs font-semibold text-ink">
                Matrícula: {formatMoney(cohort.ordenEnAnio === 2 ? pricing?.matriculaCohorte2 : pricing?.matriculaCohorte1) || 'A definir'}
              </span>
            </button>
          ))}
        </GlassCard>
      )}

      {state.paso === 'documentacion' && (
        <GlassCard className="flex flex-col gap-5 p-5">
          <div>
            <div className="font-heading text-[15px] font-semibold text-ink">Datos personales</div>
            <div className="mt-1 text-xs text-ink-faint">Confirmá o completá los datos que no pediste en el registro.</div>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Nombre</div>
              <Input value={usuario?.nombre ?? ''} disabled />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Apellido</div>
              <Input value={usuario?.apellido ?? ''} disabled />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Email</div>
              <Input
                type="email"
                placeholder="tu@email.com"
                value={state.email}
                onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'email', valor: e.target.value })}
                required
              />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">DNI</div>
              <Input
                placeholder="DNI sin puntos"
                value={state.dni}
                onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'dni', valor: e.target.value })}
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-[2fr_1fr_1fr]">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Calle</div>
              <Input value={state.street} onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'street', valor: e.target.value })} required />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Número</div>
              <Input value={state.number} onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'number', valor: e.target.value })} required />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Piso/Depto</div>
              <Input value={state.floor} onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'floor', valor: e.target.value })} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-3">
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Ciudad</div>
              <Input value={state.city} onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'city', valor: e.target.value })} required />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Provincia</div>
              <Input value={state.state} onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'state', valor: e.target.value })} />
            </div>
            <div>
              <div className="mb-1.5 text-xs font-semibold text-ink-soft">Código postal</div>
              <Input
                value={state.postalCode}
                onChange={(e) => dispatch({ type: 'SET_CAMPO', campo: 'postalCode', valor: e.target.value })}
              />
            </div>
          </div>

          {tipo === 'carrera' && (
            <div className="flex flex-col gap-3">
              <div>
                <div className="font-heading text-[15px] font-semibold text-ink">Documentación</div>
                <div className="mt-1 text-xs text-ink-faint">Subí una foto o escaneo de cada documento.</div>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {DOCUMENTOS_CARRERA.map((doc) => {
                  const archivo = state.documentos[doc.tipo];
                  return (
                    <label
                      key={doc.tipo}
                      className={cn(
                        'flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed px-3 py-5 text-center transition-colors hover:bg-neutral-bg',
                        archivo ? 'border-success bg-success-bg' : 'border-border',
                      )}
                    >
                      <span className="text-[12.5px] font-semibold text-ink">{doc.label}</span>
                      {archivo ? (
                        <span className="flex items-center gap-1 text-[11px] font-semibold text-success">
                          <CircleCheck className="h-3.5 w-3.5" /> Cargado correctamente
                        </span>
                      ) : (
                        <span className="text-[11px] text-ink-faint">Tocá para subir o sacar una foto</span>
                      )}
                      <input
                        type="file"
                        accept="image/*,.pdf"
                        capture="environment"
                        className="hidden"
                        onChange={(e) => dispatch({ type: 'SET_DOCUMENTO', tipo: doc.tipo, file: e.target.files?.[0] })}
                      />
                    </label>
                  );
                })}
              </div>
            </div>
          )}

          {tipo === 'carrera' && state.error && (
            <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{state.error}</div>
          )}

          <div className="flex justify-between">
            {tipo === 'carrera' ? (
              <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'cohorte' })} className="rounded-xl">
                ← Volver
              </Button>
            ) : (
              <span />
            )}
            {tipo === 'carrera' ? (
              <Button
                onClick={() => setModalConfirmarAbierto(true)}
                disabled={!solicitudCompleta() || state.enviando}
                variant="gradient"
                className="px-6"
              >
                {state.enviando ? 'Enviando…' : 'Confirmar solicitud'}
              </Button>
            ) : (
              <Button
                onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'pago' })}
                disabled={!documentacionCompleta()}
                variant="gradient"
                className="px-6"
              >
                Continuar →
              </Button>
            )}
          </div>
        </GlassCard>
      )}

      {state.paso === 'pago' && (
        <GlassCard className="flex flex-col gap-4 p-5">
          <div className="rounded-xl bg-neutral-bg px-4 py-3">
            <div className="text-xs text-ink-faint">Monto a pagar</div>
            <div className="font-heading text-xl font-bold text-ink">{formatMoney(montoAPagar) || 'A definir'}</div>
            <div className="mt-1 text-xs text-ink-faint">{tipo === 'carrera' ? 'Matrícula' : 'Primera cuota'}</div>
          </div>

          <div>
            <div className="mb-2 text-xs font-semibold text-ink-soft">Elegí el método de pago</div>
            {/* Único método disponible por ahora — se muestra como selector (no
                como texto fijo) para que agregar otro método el día de mañana no
                implique rediseñar este paso. */}
            <div className="flex items-center gap-3 rounded-xl border border-brand bg-accent-soft px-4 py-3">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand to-accent-2 text-sm text-white">
                ✓
              </div>
              <div>
                <div className="text-[13.5px] font-bold text-ink">Transferencia bancaria</div>
                <div className="text-xs text-ink-faint">Transferí y subí el comprobante de pago.</div>
              </div>
            </div>
          </div>

          <BankAccountDetails />

          {state.error && <div className="rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">{state.error}</div>}

          <label
            className={cn(
              'flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-xl border border-dashed px-4 py-8 text-center transition-colors hover:bg-neutral-bg',
              state.comprobante ? 'border-success bg-success-bg' : 'border-border',
            )}
          >
            <span className="text-[13px] font-semibold text-ink">
              {state.comprobante ? state.comprobante.name : 'Subir comprobante de pago'}
            </span>
            {state.comprobante ? (
              <span className="flex items-center gap-1 text-xs font-semibold text-success">
                <CircleCheck className="h-3.5 w-3.5" /> Cargado correctamente
              </span>
            ) : (
              <span className="text-xs text-ink-faint">PDF, JPG o PNG</span>
            )}
            <input
              type="file"
              accept="image/*,.pdf"
              capture="environment"
              className="hidden"
              onChange={(e) => dispatch({ type: 'ELEGIR_COMPROBANTE', file: e.target.files?.[0] })}
            />
          </label>

          <div className="flex justify-between gap-3">
            <Button variant="outline" onClick={() => dispatch({ type: 'IR_A_PASO', paso: 'documentacion' })} className="rounded-xl">
              ← Volver
            </Button>
            <Button
              onClick={() => setModalConfirmarAbierto(true)}
              disabled={!solicitudCompleta() || state.enviando}
              variant="gradient"
            >
              {state.enviando ? 'Enviando…' : 'Confirmar solicitud'}
            </Button>
          </div>
        </GlassCard>
      )}

      <Dialog open={modalConfirmarAbierto} onOpenChange={setModalConfirmarAbierto}>
        <DialogContent className="max-w-sm rounded-xl border border-border bg-surface p-6 text-ink backdrop-blur-[28px] backdrop-saturate-[1.6]">
          <DialogHeader>
            <div className="font-heading text-base font-bold text-ink">Confirmar solicitud</div>
          </DialogHeader>
          <div className="text-[13px] text-ink-soft">
            {tipo === 'carrera' ? (
              <>
                Vas a enviar tu solicitud para <strong className="text-ink">{target.name}</strong>. No pagás nada ahora: si la aprueban,
                recibís la factura de la matrícula ({formatMoney(montoAPagar) || 'monto a definir'}) para pagarla.
              </>
            ) : (
              <>
                Vas a enviar tu solicitud para <strong className="text-ink">{target.name}</strong> por{' '}
                <strong className="text-ink">{formatMoney(montoAPagar) || 'el monto informado'}</strong>. Una vez enviada, no vas a poder
                modificarla hasta que la revisen.
              </>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalConfirmarAbierto(false)} className="rounded-xl">
              Cancelar
            </Button>
            <Button onClick={confirmarSolicitud} variant="gradient">
              Sí, confirmar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
