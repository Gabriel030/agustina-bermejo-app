import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { EmptyState } from '@/components/EmptyState';
import { Skeleton } from '@/components/Skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { EstadoSolicitudBadge } from '@/features/enrollment/components/EstadoSolicitudBadge';
import { useMisSolicitudesCarrera, useMisSolicitudesCurso } from '@/features/enrollment/hooks/useCatalogo';

// Combina solicitudes de carrera (ProgramEnrollmentApplication, status propio)
// y de curso (CourseEnrollmentApplication, paymentStatus del PaymentReceipt) en
// una sola bandeja — dos vocabularios de estado distintos, un mismo componente
// visual (EstadoSolicitudBadge ya sabe mapear ambos). Solo las filas de carrera
// navegan a un detalle: no existe pantalla de detalle de solicitud de curso
// (no hace falta — no tiene documentación que subir, ver spec funcional).
export function TabMisSolicitudes() {
  const { data: solicitudesCarrera, isLoading: cargandoCarrera, isError: errorCarrera } = useMisSolicitudesCarrera();
  const { data: solicitudesCurso, isLoading: cargandoCurso, isError: errorCurso } = useMisSolicitudesCurso();

  const filas = useMemo(() => {
    const carrera = (solicitudesCarrera ?? []).map((s) => ({
      key: `carrera-${s.id}`,
      tipo: 'Carrera',
      nombre: s.programName,
      solicitud: s,
      href: `/mis-solicitudes/${s.id}`,
    }));
    const curso = (solicitudesCurso ?? []).map((s) => ({
      key: `curso-${s.id}`,
      tipo: 'Curso',
      nombre: s.courseName,
      solicitud: { status: s.courseEnrollmentStatus === 'WAITLISTED' ? 'WAITLISTED' : s.paymentStatus },
      href: null,
    }));
    return [...carrera, ...curso];
  }, [solicitudesCarrera, solicitudesCurso]);

  if (errorCarrera || errorCurso) {
    return <GlassCard className="p-6 text-sm text-danger">No pudimos cargar tus solicitudes. Probá recargar la página.</GlassCard>;
  }
  if (cargandoCarrera || cargandoCurso) {
    return (
      <GlassCard className="flex flex-col gap-3 p-5" aria-busy="true">
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
      </GlassCard>
    );
  }
  if (filas.length === 0) {
    return (
      <GlassCard className="p-2">
        <EmptyState
          title="Todavía no tenés solicitudes"
          description="Cuando pidas el alta en una carrera o te inscribas a un curso, vas a ver acá el estado de cada trámite. Elegí uno en las otras pestañas para empezar."
        />
      </GlassCard>
    );
  }

  return (
    <GlassCard className="overflow-x-auto p-2">
      <Table className="min-w-[420px]">
        <TableHeader>
          <TableRow>
            <TableHead>Tipo</TableHead>
            <TableHead>Nombre</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead className="w-8" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {filas.map((fila) => (
            <TableRow key={fila.key}>
              <TableCell className="text-xs font-semibold text-ink-faint">{fila.tipo}</TableCell>
              <TableCell className="max-w-[220px] truncate font-bold text-ink">
                {fila.href ? (
                  <Link to={fila.href} className="block max-w-full truncate font-bold text-ink hover:underline">
                    {fila.nombre}
                  </Link>
                ) : (
                  fila.nombre
                )}
              </TableCell>
              <TableCell>
                <EstadoSolicitudBadge solicitud={fila.solicitud} />
              </TableCell>
              <TableCell>{fila.href && <ChevronRight size={16} className="text-ink-faint" aria-hidden="true" />}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </GlassCard>
  );
}
