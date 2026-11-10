import { GlassCard } from '@/components/GlassCard';
import { StatusPill } from '@/components/StatusPill';
import { Skeleton } from '@/components/Skeleton';
import { Button } from '@/components/ui/button';
import { formatDate, formatMoney, formatPeriod, invoiceStatusInfo } from '@/lib/billing';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

function Header({ children }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <h2 className="font-heading text-[16px] font-semibold text-ink">Situación de deuda</h2>
      {children}
    </div>
  );
}

export function GraduationDebtCard({ summary, isLoading, isError, onRetry, financeAction }) {
  if (isLoading) {
    return (
      <GlassCard className="flex flex-col gap-3 p-4">
        <Header />
        <Skeleton className="h-24" />
      </GlassCard>
    );
  }
  if (isError || !summary) {
    return (
      <GlassCard className="flex flex-col items-start gap-3 p-4">
        <Header />
        <p role="alert" className="text-[13px] text-ink-soft">No pudimos cargar la situación de deuda.</p>
        <Button size="sm" variant="outline" onClick={onRetry}>Reintentar</Button>
      </GlassCard>
    );
  }
  if (!summary.mustCheckDebt) {
    return (
      <GlassCard className="flex flex-col gap-2 p-4">
        <Header><StatusPill tone="neutral">No se controla</StatusPill></Header>
        <p className="text-[12.5px] text-ink-faint">El control de deuda está desactivado: no se muestran cuotas.</p>
      </GlassCard>
    );
  }
  const installments = summary.installments ?? [];
  if (installments.length === 0) {
    return (
      <GlassCard className="flex flex-col gap-2 p-4">
        <Header><StatusPill tone="success">Al día</StatusPill></Header>
        <p className="text-[12.5px] text-ink-faint">Sin cuotas vencidas ni impagas.</p>
      </GlassCard>
    );
  }
  return (
    <GlassCard className="flex flex-col gap-3 p-4">
      <Header><StatusPill tone="danger">Con deuda</StatusPill></Header>
      <div className="overflow-x-auto">
        <Table className="w-full min-w-[420px] border-collapse text-[13.5px]">
          <TableCaption className="sr-only">Cuotas adeudadas</TableCaption>
          <TableHeader>
            <TableRow className="text-left text-[11px] uppercase tracking-wider text-ink-faint">
              <TableHead scope="col" className="px-2.5 py-2">Cuota</TableHead>
              <TableHead scope="col" className="px-2.5 py-2">Estado</TableHead>
              <TableHead scope="col" className="px-2.5 py-2 text-right">Monto</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {installments.map((installment) => {
              const status = invoiceStatusInfo(installment.status);
              return (
                <TableRow key={`${installment.period}-${installment.dueDate}`} className="border-t border-border/50">
                  <TableCell className="px-2.5 py-2.5 text-ink">
                    {formatPeriod(installment.period)}
                    <span className="block text-[12px] text-ink-faint">Vence {formatDate(installment.dueDate)}</span>
                  </TableCell>
                  <TableCell className="px-2.5 py-2.5"><StatusPill tone={status.tone}>{status.label}</StatusPill></TableCell>
                  <TableCell className="px-2.5 py-2.5 text-right tabular-nums text-ink">{formatMoney(installment.amount)}</TableCell>
                </TableRow>
              );
            })}
            <TableRow className="border-t border-border font-bold text-ink">
              <TableCell colSpan={2} className="px-2.5 py-2.5">Total adeudado</TableCell>
              <TableCell className="px-2.5 py-2.5 text-right tabular-nums">{formatMoney(summary.totalDue)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
      <p className="text-[12px] text-ink-faint">Datos al momento de abrir la ficha. Al verificar se vuelven a controlar.</p>
      {financeAction}
    </GlassCard>
  );
}
