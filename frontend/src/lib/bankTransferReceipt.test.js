import { describe, expect, it } from 'vitest';
import {
  buildReceiptSubmission,
  coverageOf,
  formatAmountInput,
  latestReceiptCovering,
  orderedPayableInvoices,
  parseAmountInput,
  isUnconfirmedSubmission,
  receiptOutcomeLines,
  receiptResultRows,
  receiptFormErrors,
  receiptSubmissionFailure,
  todayIsoDate,
} from '@/lib/bankTransferReceipt';

const money = (amount) => ({ amount, currency: 'ARS' });

describe('parseAmountInput', () => {
  it('readsArgentineFormattedAmounts', () => {
    expect(parseAmountInput('52.800,50')).toBe(52800.5);
    expect(parseAmountInput('$ 105.600,00')).toBe(105600);
  });

  it('readsPlainNumbersAndThousandsDots', () => {
    expect(parseAmountInput('60000')).toBe(60000);
    expect(parseAmountInput('60.000')).toBe(60000);
    expect(parseAmountInput('1234.5')).toBe(1234.5);
  });

  it('returnsNaNForEmptyText', () => {
    expect(parseAmountInput('')).toBeNaN();
    expect(parseAmountInput('abc')).toBeNaN();
  });

  it('roundTripsWithTheDisplayFormat', () => {
    expect(parseAmountInput(formatAmountInput(158400))).toBe(158400);
  });
});

describe('coverageOf', () => {
  const invoices = [{ outstanding: money('100.00') }, { outstanding: money('100.00') }, { outstanding: money('100.00') }];

  it('coversInstallmentsInOrderAndMarksTheFirstUncoveredAsShortWithWhatIsMissing', () => {
    const coverage = coverageOf(invoices, 150);
    expect(coverage.map((entry) => entry.state)).toEqual(['covered', 'short', 'none']);
    expect(coverage[1].missing).toBe(50);
  });

  it('neverSkipsAShortInstallmentToCoverALaterOne', () => {
    expect(coverageOf([{ outstanding: money('200.00') }, { outstanding: money('50.00') }], 100).map((entry) => entry.state)).toEqual([
      'short',
      'none',
    ]);
  });

  it('coversNothingWhenTheAmountIsZeroOrInvalid', () => {
    expect(coverageOf(invoices, 0).map((entry) => entry.state)).toEqual(['none', 'none', 'none']);
    expect(coverageOf(invoices, NaN).map((entry) => entry.state)).toEqual(['none', 'none', 'none']);
  });
});

describe('orderedPayableInvoices', () => {
  const base = { invoiceType: 'MONTHLY_INSTALLMENT', documentStatus: 'ISSUED', outstandingAmount: money('10.00') };

  it('ordersByDueDateThenIdAndSeparatesTheOnesUnderReview', () => {
    const { selectable, underReview } = orderedPayableInvoices([
      { ...base, id: 3, dueDate: '2026-11-10', paymentStatus: 'PENDING', lines: [{ period: '2026-11' }] },
      { ...base, id: 2, dueDate: '2026-09-10', paymentStatus: 'OVERDUE', lines: [{ period: '2026-09' }] },
      { ...base, id: 1, dueDate: '2026-09-10', paymentStatus: 'OVERDUE', lines: [{ period: '2026-09' }] },
      { ...base, id: 4, dueDate: '2026-08-10', paymentStatus: 'PAYMENT_UNDER_REVIEW', lines: [{ period: '2026-08' }] },
      { ...base, id: 5, dueDate: '2026-07-10', paymentStatus: 'PAID', lines: [{ period: '2026-07' }] },
    ]);
    expect(selectable.map((invoice) => invoice.id)).toEqual([1, 2, 3]);
    expect(underReview.map((invoice) => invoice.id)).toEqual([4]);
  });
});

describe('receiptFormErrors', () => {
  const valid = {
    file: new File(['x'], 'a.pdf'),
    operationNumber: '123',
    originAccount: '0170099220000067797370',
    holderName: 'Valentina',
    amount: 100,
    firstInvoiceOutstanding: 100,
  };

  it('acceptsACbuOrAnAlias', () => {
    expect(receiptFormErrors(valid)).toEqual({});
    expect(receiptFormErrors({ ...valid, originAccount: 'valentina.suarez.mp' })).toEqual({});
  });

  it('rejectsMalformedOriginAccounts', () => {
    expect(receiptFormErrors({ ...valid, originAccount: '12345' }).originAccount).toBeDefined();
    expect(receiptFormErrors({ ...valid, originAccount: 'alias con espacios' }).originAccount).toBeDefined();
  });

  it('rejectsAnAmountBelowTheFirstInstallment', () => {
    expect(receiptFormErrors({ ...valid, amount: 99.99 }).amount).toBe('El monto no alcanza a cubrir la primera cuota.');
  });

  it('acceptsAnAmountAboveTheSelectedTotal', () => {
    expect(receiptFormErrors({ ...valid, amount: 5000 })).toEqual({});
  });
});

describe('buildReceiptSubmission', () => {
  it('putsEveryDetailInTheQueryStringAndOnlyTheFileInTheBody', () => {
    const { path, body } = buildReceiptSubmission({
      file: new File(['x'], 'a.pdf'),
      invoiceIds: [61, 62],
      amount: 105600,
      currency: 'ARS',
      operationNumber: ' 4821937 ',
      originAccount: 'valentina.suarez.mp',
      holderName: 'Valentina Suárez',
      bankName: '  ',
      transferDate: '2026-10-28',
    });
    const params = new URL(`http://x${path}`).searchParams;
    expect(path.startsWith('/me/bank-transfer-receipts?')).toBe(true);
    expect(params.getAll('invoiceIds')).toEqual(['61', '62']);
    expect(params.get('declaredAmount')).toBe('105600.00');
    expect(params.get('operationNumber')).toBe('4821937');
    expect(params.has('bankName')).toBe(false);
    expect([...body.keys()]).toEqual(['file']);
  });
});

describe('receiptSubmissionFailure', () => {
  it('mapsEachDetailViolationToItsField', () => {
    const failure = receiptSubmissionFailure({
      code: 'BANK_TRANSFER_DETAILS_REQUIRED',
      violations: [{ code: 'BANK_TRANSFER_HOLDER_NAME_REQUIRED' }, { code: 'BANK_TRANSFER_RECEIPT_FILE_TYPE_NOT_ALLOWED' }],
    });
    expect(failure.fieldErrors.holderName).toBe('Ingresá el titular de la cuenta de origen.');
    expect(failure.fieldErrors.file).toBe('El comprobante tiene que ser un PDF, JPG o PNG.');
    expect(failure.message).toBeNull();
  });

  it('flagsTheDuplicateTransfer', () => {
    expect(receiptSubmissionFailure({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }).duplicate).toBe(true);
  });

  it('fallsBackToAGenericMessageForUnknownErrors', () => {
    expect(receiptSubmissionFailure({ status: 500 }).message).toBe('No pudimos enviar el comprobante. Probá de nuevo.');
  });
});

describe('latestReceiptCovering', () => {
  it('picksTheNewestReceiptThatIncludesTheInstallment', () => {
    const older = { id: 1, invoiceId: 61, invoices: [{ invoiceId: 61 }], uploadedAt: '2026-10-01T10:00:00' };
    const newer = { id: 2, invoiceId: 60, invoices: [{ invoiceId: 60 }, { invoiceId: 61 }], uploadedAt: '2026-10-02T10:00:00' };
    expect(latestReceiptCovering([older, newer], 61).id).toBe(2);
    expect(latestReceiptCovering([older, newer], 99)).toBeUndefined();
  });
});

describe('todayIsoDate', () => {
  it('formatsTheLocalDateWithPadding', () => {
    expect(todayIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});

describe('buildReceiptSubmission endpoint', () => {
  const base = {
    file: new File(['x'], 'c.pdf'),
    invoiceIds: [61, 62],
    amount: 200000,
    currency: 'ARS',
    operationNumber: ' 4821937 ',
    originAccount: 'valentina.suarez.mp',
    holderName: 'Valentina Suárez',
    bankName: '',
    transferDate: '2026-10-28',
  };

  it('targets the own account endpoint when no student is given', () => {
    expect(buildReceiptSubmission(base).path).toMatch(/^\/me\/bank-transfer-receipts\?/);
  });

  it('targets the on behalf of endpoint when a student is given', () => {
    const { path } = buildReceiptSubmission({ ...base, studentId: 2 });
    expect(path).toMatch(/^\/students\/2\/bank-transfer-receipts\?/);
    expect(new URL(`http://x${path}`).searchParams.get('operationNumber')).toBe('4821937');
  });
});

describe('isUnconfirmedSubmission', () => {
  it('treats network failures and server errors as unconfirmed but not client errors', () => {
    expect(isUnconfirmedSubmission(new TypeError('Failed to fetch'))).toBe(true);
    expect(isUnconfirmedSubmission({ status: 503 })).toBe(true);
    expect(isUnconfirmedSubmission({ status: 409 })).toBe(false);
    expect(isUnconfirmedSubmission({ status: 400 })).toBe(false);
  });
});

describe('receiptOutcomeLines for staff', () => {
  const invoices = [
    { id: 1, title: 'Cuota sep', outstanding: money('100000.00'), paymentStatus: 'OVERDUE' },
    { id: 2, title: 'Cuota nov', outstanding: money('100000.00'), paymentStatus: 'PENDING' },
  ];

  it('speaks about the student, the protected installment and the pending state of the receipt', () => {
    const texts = receiptOutcomeLines({ invoices, count: 2, amount: 250000, currency: 'ARS', audience: 'staff' }).map((line) => line.text);
    expect(texts.some((text) => text.includes('Se transfirieron') && text.includes('saldo a favor del alumno'))).toBe(true);
    expect(texts).toContain('Cargado a tiempo: Cuota nov no genera recargo mientras se revisa.');
    expect(texts).toContain('El comprobante queda pendiente en la pestaña Comprobantes. No se aprueba solo.');
  });

  it('keeps the student wording by default', () => {
    const texts = receiptOutcomeLines({ invoices, count: 2, amount: 200000, currency: 'ARS' }).map((line) => line.text);
    expect(texts).toContain('Te avisamos cuando la revisen. Suele tardar 1 o 2 días hábiles.');
  });
});

describe('receiptResultRows', () => {
  const invoices = [
    { id: 1, title: 'sep', dueDate: '2026-09-10', outstanding: money('100000.00'), paymentStatus: 'OVERDUE' },
    { id: 2, title: 'oct', dueDate: '2026-10-10', outstanding: money('100000.00'), paymentStatus: 'OVERDUE' },
    { id: 3, title: 'nov', dueDate: '2026-11-10', outstanding: money('100000.00'), paymentStatus: 'PENDING' },
  ];

  it('protects only covered installments that were not overdue', () => {
    expect(receiptResultRows(invoices, 300000).map((row) => row.outcome)).toEqual(['overdue', 'overdue', 'protected']);
  });

  it('leaves the installments the amount does not reach as uncovered', () => {
    expect(receiptResultRows(invoices, 150000).map((row) => row.outcome)).toEqual(['overdue', 'uncovered', 'uncovered']);
  });
});
