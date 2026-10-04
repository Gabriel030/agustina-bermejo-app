import { useState } from 'react';
import { AlertTriangle, Settings2 } from 'lucide-react';
import { toast } from 'sonner';
import { GlassCard } from '@/components/GlassCard';
import { Skeleton } from '@/components/Skeleton';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  useProgramBillingTemplates,
  useUpdateYearBillingConfiguration,
  useBillingConfigurationAlerts,
} from '@/features/gestion-academica/hooks/useCohorts';
import { MESES, descripcionMatriculas } from '@/features/gestion-academica/lib/aniosLectivos';
import { useTerminos } from '@/features/oferta-academica/hooks/useTerminos';

const SELECT_CLASS =
  'w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-ink focus:outline-none focus:ring-1 focus:ring-accent';
const LABEL_CLASS = 'text-[11px] font-bold uppercase tracking-widest text-ink-faint';

function ConfigurarAnioDialog({ template, titulo, onClose }) {
  const update = useUpdateYearBillingConfiguration();
  const [cantidad, setCantidad] = useState(String(template.enrollmentFeeCount ?? 1));
  const [primerMes, setPrimerMes] = useState(String(template.firstEnrollmentFeeMonth ?? 2));
  const [segundoMes, setSegundoMes] = useState(String(template.secondEnrollmentFeeMonth ?? 7));
  const [anticipacion, setAnticipacion] = useState(String(template.enrollmentFeeLeadMonths ?? 1));
  const [diasTardia, setDiasTardia] = useState(String(template.lateEnrollmentFeeDueDays ?? 10));
  const [pagaEnPausa, setPagaEnPausa] = useState(template.paysWhilePaused ?? false);
  const [bloqueaMaterias, setBloqueaMaterias] = useState(template.blocksEnrollment ?? true);
  const [bloqueaMesas, setBloqueaMesas] = useState(template.blocksExamRegistration ?? true);
  const [bloqueaCertificados, setBloqueaCertificados] = useState(template.blocksCertificates ?? true);
  const [soloFinales, setSoloFinales] = useState(template.requireEnrollmentFeeForFinalExamOnlyStudents ?? false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (cantidad === '2' && Number(segundoMes) <= Number(primerMes)) {
      toast.error('El mes de la segunda matrícula tiene que ser posterior al de la primera.');
      return;
    }
    try {
      await update.mutateAsync({
        templateId: template.id,
        enrollmentFeeCount: Number(cantidad),
        firstEnrollmentFeeMonth: Number(primerMes),
        ...(cantidad === '2' ? { secondEnrollmentFeeMonth: Number(segundoMes) } : {}),
        enrollmentFeeLeadMonths: Number(anticipacion),
        lateEnrollmentFeeDueDays: Number(diasTardia),
        paysWhilePaused: pagaEnPausa,
        blocksEnrollment: bloqueaMaterias,
        blocksExamRegistration: bloqueaMesas,
        blocksCertificates: bloqueaCertificados,
        requireEnrollmentFeeForFinalExamOnlyStudents: soloFinales,
      });
      toast.success('Configuración del año lectivo guardada.');
      onClose();
    } catch {
      toast.error('No se pudo guardar la configuración.');
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Configurar cobro — {titulo}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <fieldset className="flex flex-col gap-3">
            <legend className={LABEL_CLASS}>Cuándo se cobra</legend>
            <div className="flex flex-col gap-1">
              <label htmlFor="anio-cantidad" className={LABEL_CLASS}>Cantidad de matrículas por año</label>
              <select id="anio-cantidad" value={cantidad} onChange={(e) => setCantidad(e.target.value)} className={SELECT_CLASS}>
                <option value="1">1 por año (cubre los dos cuatrimestres)</option>
                <option value="2">2 por año (una por cuatrimestre)</option>
              </select>
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="anio-primer-mes" className={LABEL_CLASS}>Mes de la {cantidad === '2' ? 'primera ' : ''}matrícula</label>
              <select id="anio-primer-mes" value={primerMes} onChange={(e) => setPrimerMes(e.target.value)} className={SELECT_CLASS}>
                {MESES.map((nombre, i) => <option key={nombre} value={String(i + 1)}>{nombre}</option>)}
              </select>
            </div>
            {cantidad === '2' && (
              <div className="flex flex-col gap-1">
                <label htmlFor="anio-segundo-mes" className={LABEL_CLASS}>Mes de la segunda matrícula</label>
                <select id="anio-segundo-mes" value={segundoMes} onChange={(e) => setSegundoMes(e.target.value)} className={SELECT_CLASS}>
                  {MESES.map((nombre, i) => <option key={nombre} value={String(i + 1)}>{nombre}</option>)}
                </select>
              </div>
            )}
            <div className="flex flex-col gap-1">
              <label htmlFor="anio-anticipacion" className={LABEL_CLASS}>Meses de anticipación a la inscripción</label>
              <input id="anio-anticipacion" type="number" min="0" max="6" value={anticipacion}
                onChange={(e) => setAnticipacion(e.target.value)} className={SELECT_CLASS} />
              <p className="text-[12px] text-ink-faint">La matrícula se emite entre el 1 y el 5 del mes que corresponde a esta anticipación antes de que abra la inscripción del cuatrimestre.</p>
            </div>
          </fieldset>

          <fieldset className="flex flex-col gap-2">
            <legend className={LABEL_CLASS}>Qué bloquea la matrícula impaga</legend>
            <label className="flex items-center gap-2 text-[13px] text-ink">
              <input type="checkbox" checked={bloqueaMaterias} onChange={(e) => setBloqueaMaterias(e.target.checked)} />
              Inscripción a materias
            </label>
            <label className="flex items-center gap-2 text-[13px] text-ink">
              <input type="checkbox" checked={bloqueaMesas} onChange={(e) => setBloqueaMesas(e.target.checked)} />
              Inscripción a mesas de examen
            </label>
            <label className="flex items-center gap-2 text-[13px] text-ink">
              <input type="checkbox" checked={bloqueaCertificados} onChange={(e) => setBloqueaCertificados(e.target.checked)} />
              Pedido de certificados
            </label>
          </fieldset>

          <div className="flex flex-col gap-1">
            <label htmlFor="anio-solo-finales" className={LABEL_CLASS}>Alumnos que solo rinden finales: ¿deben pagar matrícula?</label>
            <select id="anio-solo-finales" value={soloFinales ? 'si' : 'no'} onChange={(e) => setSoloFinales(e.target.value === 'si')} className={SELECT_CLASS}>
              <option value="no">No</option>
              <option value="si">Sí</option>
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="anio-dias-tardia" className={LABEL_CLASS}>Días de vencimiento de una matrícula emitida fuera de ventana</label>
            <input id="anio-dias-tardia" type="number" min="1" max="60" value={diasTardia}
              onChange={(e) => setDiasTardia(e.target.value)} className={SELECT_CLASS} />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="anio-paga-pausa" className={LABEL_CLASS}>Alumnos en pausa académica: ¿pagan cuotas y matrícula?</label>
            <select id="anio-paga-pausa" value={pagaEnPausa ? 'si' : 'no'} onChange={(e) => setPagaEnPausa(e.target.value === 'si')} className={SELECT_CLASS}>
              <option value="no">No</option>
              <option value="si">Sí</option>
            </select>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
            <Button type="submit" variant="gradient" loading={update.isPending}>Guardar</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function AniosLectivosSection({ programId }) {
  const { data: templates, isLoading } = useProgramBillingTemplates(programId);
  const { data: alertas } = useBillingConfigurationAlerts();
  const { data: terminos } = useTerminos();
  const [editando, setEditando] = useState(null);

  const alertasDeLaCarrera = (alertas ?? []).filter((a) => String(a.programId) === String(programId));

  function tituloDe(template) {
    const term = (terminos ?? []).find((t) => String(t.id) === String(template.termId));
    return term ? `Año lectivo ${term.year}` : `Año lectivo (término ${template.termId})`;
  }

  return (
    <div className="flex flex-col gap-4">
      {alertasDeLaCarrera.length > 0 && (
        <div role="alert" className="flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2 text-[13px] text-ink">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
          <span>
            Esta carrera no tiene un año lectivo configurado para {alertasDeLaCarrera[0].period}: no se emite ninguna cuota ni
            matrícula a {alertasDeLaCarrera[0].affectedEnrollments} inscripciones activas. Cargá el año lectivo para completar lo pendiente.
          </span>
        </div>
      )}

      {isLoading ? (
        <Skeleton rows={2} />
      ) : !templates?.length ? (
        <GlassCard className="p-6">
          <EmptyState title="Sin años lectivos" description="Dirección tiene que cargar el año lectivo de la carrera para que se emitan cuotas y matrículas." />
        </GlassCard>
      ) : (
        <div className="flex flex-col gap-3">
          {templates.map((template) => (
            <GlassCard key={template.id} className="p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-[14px] font-semibold text-ink">{tituloDe(template)} — {template.recurringFeeInvoicesCount} cuotas</p>
                  <p className="text-[12px] text-ink-faint">{descripcionMatriculas(template)}</p>
                </div>
                <Button variant="outline" size="sm" onClick={() => setEditando(template)} aria-label={`Configurar cobro de ${tituloDe(template)}`}>
                  <Settings2 className="mr-1.5 h-3.5 w-3.5" />
                  Configurar cobro
                </Button>
              </div>
            </GlassCard>
          ))}
        </div>
      )}

      {editando && <ConfigurarAnioDialog template={editando} titulo={tituloDe(editando)} onClose={() => setEditando(null)} />}
    </div>
  );
}
