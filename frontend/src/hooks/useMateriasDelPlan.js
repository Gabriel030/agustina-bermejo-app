import { useMemo } from 'react';
import { useMisMatriculaciones, useMisInscripciones } from '@/features/enrollment';
import { useProgramaPlanDeEstudio } from '@/hooks/useCatalogo';
import { clasificarMateriaDelPlan, inscripcionVigente, ordenarFilasDeMaterias } from '@/lib/notas';

const ESTADO_SIN_PLAN = { APPROVED: 'aprobada', REGULAR: 'regular', FAILED: 'desaprobada' };

// Cruce de plan de estudio + inscripciones reales, compartido por TabMaterias.jsx
// (browsing → detalle), TabNotas.jsx (grilla de notas, sin navegación) y
// TabAsistencia.jsx (grilla de asistencia, sin navegación) — misma clasificación
// en-curso/aprobada/disponible/bloqueada, extraída acá para no duplicar la lógica
// entre las tres pestañas de Mis carreras (Épica 2 Fase 2).
export function useMateriasDelPlan(carreraSeleccionada) {
  const { data: matriculaciones, isLoading: cargandoMatriculaciones } = useMisMatriculaciones();
  const { data: inscripciones, isLoading: cargandoInscripciones } = useMisInscripciones();

  const activas = (matriculaciones ?? []).filter((m) => m.status !== 'WITHDRAWN');
  const matricula = activas.find((m) => m.id === carreraSeleccionada);

  const { data: planDeEstudio, isLoading: cargandoPlan } = useProgramaPlanDeEstudio(matricula?.programId);

  const filas = useMemo(() => {
    const todasLasInscripciones = inscripciones ?? [];

    // Con una carrera seleccionada: el plan de estudio completo es el universo (incluye
    // materias que el alumno nunca cursó, que es lo que hace posible "bloqueada"). Sin
    // carrera seleccionada ("todas mis carreras"): no hay un plan único que cruzar, se
    // muestran solo las inscripciones reales.
    if (matricula && planDeEstudio) {
      const inscripcionesDeEstaCarrera = todasLasInscripciones.filter((i) => i.programEnrollmentId === matricula.id);
      return planDeEstudio
        .map((programSubject) => {
          const inscripcion = inscripcionVigente(inscripcionesDeEstaCarrera.filter((i) => i.subjectId === programSubject.subjectId));
          const clasificacion = clasificarMateriaDelPlan(programSubject, inscripcion);
          return {
            key: `plan-${programSubject.id}`,
            programSubjectId: programSubject.id,
            subjectCode: programSubject.subjectCode,
            subjectName: programSubject.subjectName,
            curricularYear: programSubject.curricularYear,
            credits: programSubject.credits,
            curricularTerm: programSubject.curricularTerm,
            ...clasificacion,
          };
        })
        .sort(ordenarFilasDeMaterias);
    }

    return todasLasInscripciones
      .filter((i) => i.status !== 'WITHDRAWN')
      .map((i) => ({
        key: `inscripcion-${i.id}`,
        subjectCode: i.subjectCode,
        subjectName: i.subjectName,
        curricularYear: i.curricularYear,
        estado: ESTADO_SIN_PLAN[i.status] ?? 'en-curso',
        inscripcion: i,
      }))
      .sort(ordenarFilasDeMaterias);
  }, [inscripciones, matricula, planDeEstudio]);

  return {
    filas,
    activas,
    matricula,
    cargando: cargandoMatriculaciones || cargandoInscripciones || (!!matricula && cargandoPlan),
  };
}
