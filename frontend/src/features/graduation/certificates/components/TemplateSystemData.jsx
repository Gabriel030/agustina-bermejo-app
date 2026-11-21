import { GlassCard } from '@/components/GlassCard';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { SYSTEM_DATA } from '@/features/graduation/certificates/lib/certificateTemplate';

export function TemplateSystemData() {
  return (
    <GlassCard className="flex flex-col gap-3 p-4 md:p-5">
      <h2 className="font-heading text-size-15 font-semibold text-ink">Datos que completa el sistema</h2>
      <p className="text-size-13 text-ink-soft">No se editan acá: se escriben en los textos con su marcador y el sistema los completa al emitir.</p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Dato</TableHead>
            <TableHead>Marcador</TableHead>
            <TableHead>Ejemplo</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {SYSTEM_DATA.map((row) => (
            <TableRow key={row.marker}>
              <TableCell>{row.label}</TableCell>
              <TableCell>
                <code className="rounded-md bg-accent-soft px-1.5 py-0.5 text-size-12 text-primary">{`{${row.marker}}`}</code>
              </TableCell>
              <TableCell className="text-ink-soft">{row.example}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </GlassCard>
  );
}
