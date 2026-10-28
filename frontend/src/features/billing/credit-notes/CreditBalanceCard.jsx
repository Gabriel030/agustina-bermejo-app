import { Link } from 'react-router-dom';
import { WalletCards } from 'lucide-react';
import { GlassCard } from '@/components/GlassCard';
import { Button } from '@/components/ui/button';
import { InfoTip } from '@/components/charts/InfoTip';
import { formatMoney } from '@/lib/billing';
import { moneyValue } from '@/lib/utils';
import { useMyCreditBalance } from '../hooks/useCreditNotes';

export const MY_CREDIT_NOTES_PATH = '/alumno/pagos/notas-de-credito';

export function CreditBalanceCard() {
  const { data } = useMyCreditBalance();
  const available = data?.availableCredit;

  if (moneyValue(available) > 0) {
    return (
      <GlassCard className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-xl bg-accent-soft text-primary">
            <WalletCards className="size-5" aria-hidden="true" />
          </span>
          <div className="flex flex-col">
            <span className="flex items-center gap-1 text-xs text-ink-faint">
              Saldo a favor
              <InfoTip>Importe a tu favor en la cuenta. Para usarlo o pedir su devolución, consultá en administración.</InfoTip>
            </span>
            <span className="text-xl font-semibold tabular-nums text-ink">{formatMoney(available)}</span>
          </div>
        </div>
        <Button asChild variant="outline">
          <Link to={MY_CREDIT_NOTES_PATH}>Ver mis notas de crédito</Link>
        </Button>
      </GlassCard>
    );
  }

  return (
    <div className="flex justify-end">
      <Link to={MY_CREDIT_NOTES_PATH} className="text-sm font-semibold text-brand hover:underline">
        Mis notas de crédito
      </Link>
    </div>
  );
}
