import { GlassCard } from '@/components/GlassCard';
import { InfoTip } from '@/components/charts/InfoTip';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatMoney } from '@/lib/billing';
import { refundMethodLabel } from '../refunds/refundRequests';

function sumParts(parts) {
  const cents = parts.reduce((total, part) => total + Math.round(Number(part.amount.amount) * 100), 0);
  return { amount: (cents / 100).toFixed(2), currency: parts[0].amount.currency };
}

function RefundPartsBlock({ parts }) {
  if (parts.length === 0) return null;
  return (
    <GlassCard className="overflow-hidden">
      <div className="flex items-center gap-1 px-5 pt-5 pb-2">
        <h2 className="font-heading text-base font-semibold text-ink">Reparto por pago</h2>
        <InfoTip>El monto se reparte entre los pagos más nuevos primero. Cada parte se devuelve por el mismo método del pago original.</InfoTip>
      </div>
      <Table style={{ minWidth: 520 }}>
        <TableHeader>
          <TableRow>
            <TableHead>Pago de origen</TableHead>
            <TableHead className="text-right">Monto</TableHead>
            <TableHead>Destino</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {parts.map((part, index) => (
            <TableRow key={part.refundId ?? `${part.paymentId}-${index}`}>
              <TableCell>
                <div className="flex flex-col">
                  <span className="font-medium text-ink">Pago #{part.paymentId}</span>
                  <span className="text-xs text-ink-faint">{refundMethodLabel(part.methodType)}</span>
                </div>
              </TableCell>
              <TableCell className="text-right font-semibold tabular-nums text-ink">{formatMoney(part.amount)}</TableCell>
              <TableCell className="whitespace-normal">
                {part.destinationAccount ? (
                  <div className="flex flex-col">
                    <span className="text-ink">{part.destinationAccount}</span>
                    {part.destinationHolder && <span className="text-xs text-ink-faint">{part.destinationHolder}</span>}
                  </div>
                ) : (
                  <span className="text-ink-faint">—</span>
                )}
              </TableCell>
            </TableRow>
          ))}
          <TableRow className="hover:bg-transparent">
            <TableCell className="font-semibold text-ink">Total</TableCell>
            <TableCell className="text-right font-semibold tabular-nums text-ink">{formatMoney(sumParts(parts))}</TableCell>
            <TableCell />
          </TableRow>
        </TableBody>
      </Table>
    </GlassCard>
  );
}

function ChangeRowsBlock({ rows }) {
  if (rows.length === 0) return null;
  return (
    <GlassCard className="overflow-hidden">
      <h2 className="px-5 pt-5 pb-2 font-heading text-base font-semibold text-ink">Qué cambia</h2>
      <Table style={{ minWidth: 520 }}>
        <TableHeader>
          <TableRow>
            <TableHead>Concepto</TableHead>
            <TableHead className="text-right">Hoy</TableHead>
            <TableHead className="text-right">Nuevo</TableHead>
            <TableHead className="text-right">Diferencia</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.label}>
              <TableCell className="whitespace-normal text-ink">{row.label}</TableCell>
              <TableCell className="text-right tabular-nums text-ink-soft">{row.today}</TableCell>
              <TableCell className="text-right font-semibold tabular-nums text-ink">{row.proposed}</TableCell>
              <TableCell className="text-right tabular-nums text-ink-soft">{row.difference}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </GlassCard>
  );
}

export function DetailBlock({ block }) {
  if (block?.type === 'REFUND_PARTS') return <RefundPartsBlock parts={block.refundParts ?? []} />;
  if (block?.type === 'CHANGE') return <ChangeRowsBlock rows={block.changeRows ?? []} />;
  return null;
}
