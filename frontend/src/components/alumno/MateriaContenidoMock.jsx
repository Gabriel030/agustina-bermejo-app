import { GlassCard } from '@/components/GlassCard';

// Contenido mockeado (objetivos/temario/cronograma) — no existe backend real
// todavía para syllabus de una materia (decisión de producto, Épica 2 Fase 2:
// "poné más información mockeada para ver cómo queda"). Queda acotado a este
// único componente para que el día que exista un endpoint real
// (ej. GET /program-subjects/{id}/syllabus) el reemplazo no toque el resto de
// AlumnoMateriaDetalle.jsx. Fallback genérico por si la materia no tiene una
// entrada específica en el mock.
const CONTENIDO_POR_MATERIA = {
  Álgebra: {
    objetivos: [
      'Comprender los fundamentos del álgebra lineal aplicada al diseño.',
      'Resolver sistemas de ecuaciones y operar con matrices y vectores.',
      'Aplicar conceptos de proporción y escala a composiciones visuales.',
    ],
    temario: ['Números y operaciones básicas', 'Matrices y determinantes', 'Sistemas de ecuaciones lineales', 'Vectores en el plano'],
    cronograma: [
      { evaluacion: 'Parcial 1', fecha: '15 de abril' },
      { evaluacion: 'Parcial 2', fecha: '10 de junio' },
      { evaluacion: 'Final', fecha: 'Turno de julio' },
    ],
  },
  'Diseño Editorial': {
    objetivos: [
      'Diseñar piezas editoriales con criterio tipográfico y de retícula.',
      'Aplicar jerarquías visuales en publicaciones multi-página.',
      'Preparar archivos para impresión y para distribución digital.',
    ],
    temario: ['Retícula y grilla editorial', 'Tipografía en publicaciones', 'Maquetación multi-página', 'Preprensa y exportación'],
    cronograma: [
      { evaluacion: 'TP 1', fecha: '3 de mayo' },
      { evaluacion: 'TP 2', fecha: '21 de junio' },
      { evaluacion: 'Final', fecha: 'Turno de julio' },
    ],
  },
};

const CONTENIDO_GENERICO = {
  objetivos: ['Adquirir los conocimientos fundamentales de la materia.', 'Aplicar lo aprendido en ejercicios prácticos y trabajos de taller.'],
  temario: ['Unidad 1: Introducción', 'Unidad 2: Desarrollo de contenidos', 'Unidad 3: Integración y cierre'],
  cronograma: [
    { evaluacion: 'Parcial 1', fecha: 'A confirmar' },
    { evaluacion: 'Parcial 2', fecha: 'A confirmar' },
    { evaluacion: 'Final', fecha: 'A confirmar' },
  ],
};

export function MateriaContenidoMock({ subjectName }) {
  const contenido = CONTENIDO_POR_MATERIA[subjectName] ?? CONTENIDO_GENERICO;

  return (
    <>
      <GlassCard className="flex flex-col gap-2.5 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Objetivos</div>
        <ul className="flex flex-col gap-1.5">
          {contenido.objetivos.map((objetivo) => (
            <li key={objetivo} className="text-[13.5px] text-ink-soft">
              {objetivo}
            </li>
          ))}
        </ul>
      </GlassCard>

      <GlassCard className="flex flex-col gap-2.5 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Temario</div>
        <ol className="flex flex-col gap-1.5">
          {contenido.temario.map((unidad, i) => (
            <li key={unidad} className="flex gap-2 text-[13.5px] text-ink-soft">
              <span className="font-bold text-primary">{i + 1}.</span>
              {unidad}
            </li>
          ))}
        </ol>
      </GlassCard>

      <GlassCard className="flex flex-col gap-3 p-5">
        <div className="font-heading text-[15px] font-semibold text-ink">Cronograma de parciales</div>
        <table className="w-full border-collapse text-sm">
          <tbody>
            {contenido.cronograma.map((fila) => (
              <tr key={fila.evaluacion} className="border-b border-border last:border-b-0">
                <td className="py-2 text-[13.5px] font-semibold text-ink">{fila.evaluacion}</td>
                <td className="py-2 text-right text-[13.5px] text-ink-faint">{fila.fecha}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </GlassCard>
    </>
  );
}
