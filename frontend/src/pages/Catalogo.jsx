import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { apiFetch } from '@/api/client';

// Mapeo 1:1 a Catálogo.dc.html. El botón "Solicitar alta"/"Inscribirme" no
// pre-calcula estados de cupo/ventana de admisión de forma cosmética — el
// backend no expone esos cómputos hoy (ProgramEnrollmentApplicationService.solicitar
// no valida ventana, y el cupo real vive en CourseSubjectSection, no en
// Course.capacity) — se intenta la acción y se muestra el resultado real.
export function Catalogo() {
  const [programas, setProgramas] = useState(null);
  const [cursos, setCursos] = useState(null);
  const [errorCarga, setErrorCarga] = useState(false);
  const [solicitudesEnviadas, setSolicitudesEnviadas] = useState(new Set());
  const [inscripciones, setInscripciones] = useState(new Set());
  const [enviando, setEnviando] = useState(null);
  const [mensajes, setMensajes] = useState({});
  const { refrescarPerfil } = useAuth();

  useEffect(() => {
    Promise.all([apiFetch('/programs?status=ACTIVE'), apiFetch('/courses?status=ACTIVE')])
      .then(([programasRes, cursosRes]) => {
        setProgramas(programasRes);
        setCursos(cursosRes);
      })
      .catch(() => setErrorCarga(true));
  }, []);

  async function solicitarAlta(programId) {
    setEnviando(`programa-${programId}`);
    setMensajes((m) => ({ ...m, [`programa-${programId}`]: null }));
    try {
      await apiFetch('/me/program-enrollment-applications', {
        method: 'POST',
        body: JSON.stringify({ programId }),
      });
      setSolicitudesEnviadas((s) => new Set(s).add(programId));
    } catch (err) {
      setMensajes((m) => ({
        ...m,
        [`programa-${programId}`]: err.message === 'La operación entra en conflicto con el estado actual.'
          ? 'Ya tenés una solicitud pendiente para esta carrera.'
          : 'No pudimos enviar tu solicitud. Probá de nuevo.',
      }));
    } finally {
      setEnviando(null);
    }
  }

  async function inscribirme(courseId) {
    setEnviando(`curso-${courseId}`);
    setMensajes((m) => ({ ...m, [`curso-${courseId}`]: null }));
    try {
      await apiFetch('/me/course-enrollments', {
        method: 'POST',
        body: JSON.stringify({ courseId, sectionOverrides: {} }),
      });
      setInscripciones((s) => new Set(s).add(courseId));
      await refrescarPerfil();
    } catch (err) {
      setMensajes((m) => ({
        ...m,
        [`curso-${courseId}`]: err.message === 'La operación entra en conflicto con el estado actual.'
          ? 'Ya estás inscripto en este curso, o no queda cupo disponible.'
          : 'No pudimos completar la inscripción. Probá de nuevo.',
      }));
    } finally {
      setEnviando(null);
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-7">
      <div>
        <div className="font-heading text-2xl font-bold tracking-tight text-ink">Catálogo</div>
        <div className="mt-1 text-sm text-ink-faint">
          Elegí una carrera para solicitar tu alta, o un curso para inscribirte directo si hay cupo.
        </div>
      </div>

      {errorCarga && (
        <GlassCard className="p-6 text-sm text-danger">No pudimos cargar el catálogo. Probá recargar la página.</GlassCard>
      )}

      <section>
        <div className="mb-3 font-heading text-base font-semibold text-ink-soft">Carreras</div>
        {programas === null && !errorCarga && <div className="text-sm text-ink-faint">Cargando…</div>}
        {programas?.length === 0 && <div className="text-sm text-ink-faint">No hay carreras disponibles por el momento.</div>}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {programas?.map((programa) => (
            <GlassCard key={programa.id} className="flex flex-col p-5">
              <div className="font-heading text-base font-bold text-ink">{programa.name}</div>
              <div className="mb-3.5 mt-1.5 text-xs text-ink-faint">
                {programa.totalYears} años · Título oficial
              </div>
              {mensajes[`programa-${programa.id}`] && (
                <div className="mb-3 rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
                  {mensajes[`programa-${programa.id}`]}
                </div>
              )}
              {solicitudesEnviadas.has(programa.id) ? (
                <div className="rounded-xl bg-success-bg px-3 py-2.5 text-center text-xs font-semibold text-success">
                  Solicitud enviada
                </div>
              ) : (
                <motion.div whileHover={{ y: -2, scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                  <Button
                    onClick={() => solicitarAlta(programa.id)}
                    disabled={enviando === `programa-${programa.id}`}
                    className="w-full rounded-xl bg-gradient-to-br from-accent to-accent-2 py-5 text-[13px] font-semibold text-white"
                  >
                    {enviando === `programa-${programa.id}` ? 'Enviando…' : 'Solicitar alta'}
                  </Button>
                </motion.div>
              )}
            </GlassCard>
          ))}
        </div>
      </section>

      <section>
        <div className="mb-3 font-heading text-base font-semibold text-ink-soft">Cursos</div>
        {cursos === null && !errorCarga && <div className="text-sm text-ink-faint">Cargando…</div>}
        {cursos?.length === 0 && <div className="text-sm text-ink-faint">No hay cursos disponibles por el momento.</div>}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {cursos?.map((curso) => (
            <GlassCard key={curso.id} className="flex flex-col p-5">
              <div className="font-heading text-base font-bold text-ink">{curso.name}</div>
              <div className="mb-3.5 mt-1.5 text-xs text-ink-faint">
                {curso.durationMonths} meses{curso.schedule ? ` · ${curso.schedule}` : ''}
              </div>
              {mensajes[`curso-${curso.id}`] && (
                <div className="mb-3 rounded-lg bg-danger-bg px-3 py-2 text-xs font-medium text-danger">
                  {mensajes[`curso-${curso.id}`]}
                </div>
              )}
              {inscripciones.has(curso.id) ? (
                <div className="rounded-xl bg-success-bg px-3 py-2.5 text-center text-xs font-semibold text-success">
                  Inscripción confirmada
                </div>
              ) : (
                <motion.div whileHover={{ y: -2, scale: 1.02 }} whileTap={{ scale: 0.98 }}>
                  <Button
                    onClick={() => inscribirme(curso.id)}
                    disabled={enviando === `curso-${curso.id}`}
                    className="w-full rounded-xl bg-gradient-to-br from-accent to-accent-2 py-5 text-[13px] font-semibold text-white"
                  >
                    {enviando === `curso-${curso.id}` ? 'Inscribiendo…' : 'Inscribirme'}
                  </Button>
                </motion.div>
              )}
            </GlassCard>
          ))}
        </div>
      </section>
    </div>
  );
}
