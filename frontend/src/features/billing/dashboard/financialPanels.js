import { FEE_TYPE_LABELS } from '@/lib/billing';
import { buildRows, goalThreshold, keysWithPrefix, seriesOf, sumCounts, sumMoney, unitOf } from './dashboardMetrics';

const DISCOUNT_LABELS = {
  SCHOLARSHIP: 'Becas',
  COHORT_PROMOTION: 'Promociones',
  ANNUAL_UPFRONT: 'Pago anual',
  COHORT_ENTRY: 'Ingreso por cohorte',
  INDIVIDUAL: 'Descuento individual',
  MARKETING: 'Marketing',
};

const AGE_BUCKET_LABELS = {
  FROM_1_TO_30: '1 a 30 días',
  FROM_31_TO_60: '31 a 60 días',
  FROM_61_TO_90: '61 a 90 días',
  OVER_90: 'Más de 90 días',
};

const AGE_BUCKET_COLORS = {
  FROM_1_TO_30: 'var(--accent)',
  FROM_31_TO_60: 'var(--warning)',
  FROM_61_TO_90: 'var(--danger)',
  OVER_90: 'var(--accent-2)',
};

const COLORS = ['var(--accent)', 'var(--accent-2)', 'var(--success)', 'var(--warning)', 'var(--danger)', 'var(--ink-soft)'];

function describe(metric, key, label, extra = {}) {
  return { key, label, unit: unitOf(seriesOf(metric, key)?.unit), ...extra };
}

function typed(metric, prefix, labels, extra) {
  return keysWithPrefix(metric, prefix).map((key, index) =>
    describe(metric, key, labels[key.slice(prefix.length)] ?? key.slice(prefix.length), { color: COLORS[index % COLORS.length], ...extra }),
  );
}

function withGoal(metric, axis) {
  const threshold = goalThreshold(metric);
  if (!threshold) return undefined;
  const text = threshold.unit === 'percent' ? `${threshold.value} %` : `${threshold.value} d`;
  return { value: threshold.value, label: `Meta ${text}`, axis };
}

export const DASHBOARD_TABS = [
  { value: 'COLLECTIONS', label: 'Cobranza' },
  { value: 'ISSUING', label: 'Emisión' },
];

export const FINANCIAL_PANELS = [
  {
    id: 'on-time-payment',
    tab: 'COLLECTIONS',
    title: 'Pago a término',
    subtitle: '% de las facturas del mes pagadas hasta su vencimiento',
    info: 'Se cuenta por factura. Una factura vencida e impaga cuenta como fuera de término.',
    unavailableText: 'Disponible cuando esté el pago a término',
    build(metric) {
      const series = [describe(metric, 'onTimePercent', 'Pagadas a término', { color: COLORS[0], type: 'line' })];
      return {
        rows: buildRows(metric),
        series,
        tableSeries: [
          ...series,
          describe(metric, 'consideredInvoices', 'Facturas del mes'),
          describe(metric, 'averageDelayDays', 'Atraso promedio'),
          describe(metric, 'collected', 'Cobrado en el mes'),
        ],
        goal: withGoal(metric, 'left'),
      };
    },
  },
  {
    id: 'collections-stock',
    tab: 'COLLECTIONS',
    title: 'Deuda vencida por antigüedad',
    subtitle: 'Saldo vencido al cierre de cada mes y alumnos con deuda',
    info: 'Foto del último día de cada mes. La antigüedad cuenta desde el vencimiento de cada factura. Si falta la foto del último día se usa la última del mes y se avisa. Los meses anteriores a la primera foto figuran sin dato.',
    unavailableText: 'Disponible cuando esté el stock de deuda',
    drilldown: { path: 'facturacion?tab=para-contactar', label: 'Ver en Cobranza' },
    build(metric) {
      const buckets = Object.entries(AGE_BUCKET_LABELS).map(([bucket, label]) =>
        describe(metric, `amount.${bucket}`, label, { color: AGE_BUCKET_COLORS[bucket], type: 'area' }),
      );
      const students = describe(metric, 'students', 'Alumnos con deuda', { color: 'var(--ink)', type: 'line', axis: 'right' });
      return {
        rows: buildRows(metric),
        series: [...buckets, students],
        tableSeries: [...buckets, describe(metric, 'amount.TOTAL', 'Deuda vencida total'), students],
      };
    },
  },
  {
    id: 'receipts',
    tab: 'COLLECTIONS',
    title: 'Comprobantes de transferencia',
    subtitle: 'Subidos por resultado y días hábiles de revisión',
    info: 'Se cuenta por comprobante subido en el mes.',
    unavailableText: 'Disponible cuando estén los comprobantes',
    build(metric) {
      const series = [
        describe(metric, 'approved', 'Aprobados', { color: COLORS[2], stack: 'receipts' }),
        describe(metric, 'appliedToDebt', 'Aplicados a deuda anterior', { color: COLORS[0], stack: 'receipts' }),
        describe(metric, 'rejected', 'Rechazados', { color: COLORS[4], stack: 'receipts' }),
        describe(metric, 'averageReviewBusinessDays', 'Días hábiles de revisión', { color: COLORS[3], type: 'line', axis: 'right' }),
      ];
      return {
        rows: buildRows(metric),
        series,
        tableSeries: [describe(metric, 'uploaded', 'Subidos'), ...series, describe(metric, 'reviewedAfterTarget', 'Revisados fuera de plazo')],
        goal: withGoal(metric, 'right'),
      };
    },
  },
  {
    id: 'issued-invoices',
    tab: 'ISSUING',
    title: 'Emisión mensual',
    subtitle: 'Facturas emitidas por el proceso mensual y su monto',
    info: 'Solo facturas emitidas por el proceso mensual, por tipo de factura.',
    unavailableText: 'Disponible cuando esté la emisión',
    build(metric) {
      const amountKeys = keysWithPrefix(metric, 'amount.');
      const rows = buildRows(metric, {
        amountTotal: (row) => sumMoney(amountKeys.map((key) => row[key])),
      });
      const counts = typed(metric, 'count.', FEE_TYPE_LABELS, { stack: 'issued' });
      const total = { key: 'amountTotal', label: 'Monto emitido', unit: 'money', color: 'var(--success)', type: 'line', axis: 'right' };
      return {
        rows,
        series: [...counts, total],
        tableSeries: [...counts, describe(metric, 'zeroTotalScholarship', 'En $ 0 por beca 100 %'), total],
      };
    },
  },
  {
    id: 'discounts',
    tab: 'ISSUING',
    title: 'Becas y descuentos',
    subtitle: 'Monto resignado por tipo y % sobre el bruto',
    info: 'El porcentaje es lo resignado sobre lo que se habría facturado sin descuentos.',
    unavailableText: 'Disponible cuando estén los descuentos',
    build(metric) {
      const amounts = typed(metric, 'amount.', DISCOUNT_LABELS, { stack: 'discounts' });
      const percent = describe(metric, 'percentOfGross', '% sobre el bruto', { color: 'var(--warning)', type: 'line', axis: 'right' });
      return {
        rows: buildRows(metric),
        series: [...amounts, percent],
        tableSeries: [...amounts, describe(metric, 'totalForegone', 'Total resignado'), percent],
      };
    },
  },
  {
    id: 'overdue-detected',
    tab: 'ISSUING',
    title: 'Vencidas detectadas',
    subtitle: 'Facturas que pasaron a vencidas en el mes y su saldo',
    info: 'Cuenta en el mes de la fecha de negocio del vencimiento, no de la hora en que corrió el proceso.',
    unavailableText: 'Disponible cuando esté la detección de vencidas',
    build(metric) {
      const series = [
        describe(metric, 'count', 'Facturas vencidas', { color: COLORS[3] }),
        describe(metric, 'amount', 'Saldo pendiente', { color: COLORS[0], type: 'line', axis: 'right' }),
      ];
      return { rows: buildRows(metric), series };
    },
  },
  {
    id: 'late-fees',
    tab: 'ISSUING',
    title: 'Recargos por mora',
    subtitle: 'Notas de débito de recargo emitidas y anuladas',
    info: 'Una nota de débito por cuota y por mes.',
    unavailableText: 'Disponible cuando esté la mora',
    build(metric) {
      const series = [
        describe(metric, 'issuedCount', 'Emitidas', { color: COLORS[1] }),
        describe(metric, 'annulledCount', 'Anuladas', { color: COLORS[5] }),
        describe(metric, 'issuedAmount', 'Monto emitido', { color: COLORS[0], type: 'line', axis: 'right' }),
      ];
      return {
        rows: buildRows(metric),
        series,
        tableSeries: [...series, describe(metric, 'issuedLateCount', 'Emitidas tarde'), describe(metric, 'annulledAmount', 'Monto anulado')],
      };
    },
  },
];

export const KPI_PANEL_IDS = ['on-time-payment', 'issued-invoices', 'late-fees'];

export function issuedInvoiceCount(metric, month) {
  const index = metric?.months?.indexOf(month) ?? -1;
  if (index < 0) return null;
  return sumCounts(keysWithPrefix(metric, 'count.').map((key) => seriesOf(metric, key).values[index]));
}
