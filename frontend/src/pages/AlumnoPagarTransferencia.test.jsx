import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { Toaster } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagarTransferencia } from '@/pages/AlumnoPagarTransferencia';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Valentina', apellido: 'Suárez' } }),
}));

const API = 'http://localhost:8080';

function money(amount) {
  return { amount, currency: 'ARS' };
}

function breakdownOf(invoiceId, period) {
  return {
    invoiceId,
    feeType: 'MONTHLY_INSTALLMENT',
    period,
    baseAmount: money('48000.00'),
    adjustments: [{ type: 'SCHOLARSHIP', amount: money('-4800.00'), humanLabel: 'Beca 10%' }],
    amountDue: money('52800.00'),
    paymentsApplied: [],
    remainingBalance: money('52800.00'),
    status: 'OVERDUE',
  };
}

function installment(id, period, dueDate, paymentStatus = 'OVERDUE') {
  return {
    id,
    invoiceType: 'MONTHLY_INSTALLMENT',
    lines: [{ period }],
    dueDate,
    documentStatus: 'ISSUED',
    paymentStatus,
    outstandingAmount: money('52800.00'),
  };
}

const INSTALLMENTS = [
  installment(63, '2026-11', '2026-11-10', 'PENDING'),
  installment(61, '2026-09', '2026-09-10'),
  installment(62, '2026-10', '2026-10-10'),
];

function receiptOf(overrides = {}) {
  return {
    id: 302,
    invoiceId: 61,
    invoiceIds: [61],
    invoices: [
      { invoiceId: 61, feeType: 'MONTHLY_INSTALLMENT', period: '2026-09', totalAmount: money('52800.00'), outstandingAtSubmission: money('52800.00') },
    ],
    status: 'PENDING_REVIEW',
    declaredAmount: money('52800.00'),
    operationNumber: '4821937',
    transferDate: '2026-10-28',
    uploadedAt: '2026-10-28T10:00:00',
    rejectionCode: null,
    rejectionReason: null,
    ...overrides,
  };
}

function renderTransfer({ receipts = [], invoices = INSTALLMENTS, invoiceId = 61 } = {}) {
  server.use(
    http.get(`${API}/me/invoices/${invoiceId}/price-breakdown`, () => HttpResponse.json(breakdownOf(invoiceId, '2026-09'))),
    http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json(receipts)),
    http.get(`${API}/me/enrollment-payment-status`, () =>
      HttpResponse.json([{ enrollmentId: 9, enrollmentType: 'CourseEnrollment', paymentStatus: 'OVERDUE' }]),
    ),
    http.get(`${API}/me/course-enrollments/9/invoices`, () => HttpResponse.json(invoices)),
  );
  return renderWithProviders(
    <>
      <Toaster />
      <Routes>
        <Route path="/alumno/pagos/:invoiceId/transferencia" element={<AlumnoPagarTransferencia />} />
      </Routes>
    </>,
    { route: `/alumno/pagos/${invoiceId}/transferencia` },
  );
}

async function goToUploadStep(user) {
  await user.click(await screen.findByRole('button', { name: 'Ya transferí — subir comprobante' }));
  await screen.findByText('Datos del comprobante');
}

function pdf(name = 'comprobante.pdf') {
  return new File(['pdf'], name, { type: 'application/pdf' });
}

async function fillValidForm(user, { amount } = {}) {
  await user.upload(screen.getByLabelText('Comprobante de transferencia'), pdf());
  await user.type(screen.getByLabelText('Número de operación'), '4821937');
  await user.type(screen.getByLabelText('CBU o alias de origen'), 'valentina.suarez.mp');
  if (amount) {
    const amountInput = screen.getByLabelText('Monto transferido');
    await user.clear(amountInput);
    await user.type(amountInput, amount);
  }
}

function captureSubmission(response) {
  const captured = {};
  const respond =
    response ??
    (() => {
      server.use(http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json([receiptOf()])));
      return HttpResponse.json({ receiptId: 302 }, { status: 201 });
    });
  server.use(
    http.post(`${API}/me/bank-transfer-receipts`, async ({ request }) => {
      captured.url = new URL(request.url);
      captured.idempotencyKey = request.headers.get('Idempotency-Key');
      captured.contentType = request.headers.get('Content-Type');
      captured.calls = (captured.calls ?? 0) + 1;
      return respond();
    }),
  );
  return captured;
}

describe('AlumnoPagarTransferencia', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows which installment is being paid, the exact amount and the discount it includes', async () => {
    renderTransfer();

    expect(await screen.findByText('Cuota mensual · sep 2026')).toBeInTheDocument();
    expect(screen.getAllByText('$ 52.800,00').length).toBeGreaterThan(0);
    expect(screen.getByText('Beca')).toBeInTheDocument();
    expect(screen.getByText(/Transferí exactamente \$ 52\.800,00/)).toBeInTheDocument();
  });

  it('opensOnFollowUpStepWhenTheInstallmentAlreadyHasAReceiptUnderReview', async () => {
    renderTransfer({ receipts: [receiptOf()] });

    expect(await screen.findByText('Comprobante en revisión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ya transferí — subir comprobante' })).not.toBeInTheDocument();
  });

  it('listsInstallmentsFromOldestAndSelectsUpToTheOneBeingPaid', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoiceId: 62 });
    await goToUploadStep(user);

    const rows = within(screen.getByRole('list', { name: 'Cuotas que cubrís' })).getAllByRole('button');
    expect(rows.map((row) => within(row).getByText(/Cuota mensual/).textContent)).toEqual([
      'Cuota mensual · sep 2026',
      'Cuota mensual · oct 2026',
      'Cuota mensual · nov 2026',
    ]);
    expect(rows.map((row) => row.getAttribute('aria-pressed'))).toEqual(['true', 'true', 'false']);
    expect(screen.getByLabelText('Monto transferido')).toHaveValue('105.600,00');
  });

  it('selectingALaterInstallmentAlwaysIncludesTheOlderOnesAndSendsTheirIdsInOrder', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    await user.click(screen.getByRole('button', { name: /Cuota mensual · nov 2026/ }));
    expect(screen.getByRole('button', { name: /Cuota mensual · oct 2026/ })).toHaveAttribute('aria-pressed', 'true');
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await screen.findByText('Comprobante en revisión');
    expect(captured.url.searchParams.getAll('invoiceIds')).toEqual(['61', '62', '63']);
    expect(captured.url.searchParams.get('declaredAmount')).toBe('158400.00');
  });

  it('tappingTheLastSelectedInstallmentAgainDropsOnlyThatOne', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoiceId: 62 });
    await goToUploadStep(user);

    await user.click(screen.getByRole('button', { name: /Cuota mensual · oct 2026/ }));

    expect(screen.getByRole('button', { name: /Cuota mensual · sep 2026/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /Cuota mensual · oct 2026/ })).toHaveAttribute('aria-pressed', 'false');
  });

  it('showsAnInstallmentUnderReviewAsInformationalRowThatCannotBePicked', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoices: [...INSTALLMENTS, installment(60, '2026-08', '2026-08-10', 'PAYMENT_UNDER_REVIEW')] });
    await goToUploadStep(user);

    expect(screen.getByText('Ya está en un comprobante en revisión')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Cuota mensual/ })).toHaveLength(3);
  });

  it('describesEachInstallmentRowWithItsDueDateNote', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    const row = screen.getByRole('button', { name: /Cuota mensual · sep 2026/ });
    expect(row).toHaveAccessibleDescription('Venció el 10/09/2026');
  });

  it('prefillsTheAccountHolderWithTheStudentNameAndKeepsItEditable', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    const holder = screen.getByLabelText('Titular de la cuenta de origen');
    expect(holder).toHaveValue('Valentina Suárez');
    await user.clear(holder);
    await user.type(holder, 'Marta Suárez');
    expect(holder).toHaveValue('Marta Suárez');
  });

  it('rejectsAFileLargerThan5MbAndLetsTheStudentRemoveTheChosenOne', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    const bigFile = pdf('grande.pdf');
    Object.defineProperty(bigFile, 'size', { value: 6 * 1024 * 1024 });
    await user.upload(screen.getByLabelText('Comprobante de transferencia'), bigFile);

    expect(await screen.findByText('El archivo pesa más de 5 MB. Subí uno más liviano.')).toBeInTheDocument();
    expect(fetchSpy.mock.calls.some(([url]) => String(url).includes('bank-transfer-receipts') && !String(url).endsWith('/me/bank-transfer-receipts'))).toBe(false);

    expect(screen.getByText('grande.pdf')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar grande.pdf' }));
    await user.upload(screen.getByLabelText('Comprobante de transferencia'), pdf('bueno.pdf'));
    expect(screen.queryByText('El archivo pesa más de 5 MB. Subí uno más liviano.')).not.toBeInTheDocument();
    expect(screen.getByText('bueno.pdf')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Quitar bueno.pdf' }));
    expect(screen.queryByText('bueno.pdf')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Comprobante de transferencia')).toBeInTheDocument();
  });

  it('rejectsAFileThatIsNotAPdfJpgOrPng', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderTransfer();
    await goToUploadStep(user);

    await user.upload(screen.getByLabelText('Comprobante de transferencia'), new File(['hola'], 'notas.txt', { type: 'text/plain' }));

    expect(await screen.findByText('El comprobante tiene que ser un PDF, JPG o PNG.')).toBeInTheDocument();
    expect(screen.getByText('notas.txt')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar notas.txt' }));
    expect(screen.queryByText('notas.txt')).not.toBeInTheDocument();
    expect(screen.queryByText('El comprobante tiene que ser un PDF, JPG o PNG.')).not.toBeInTheDocument();
  });

  it('showsAFieldErrorForEachMissingDetailAndSendsNothing', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await user.clear(screen.getByLabelText('Titular de la cuenta de origen'));

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Subí el comprobante para poder enviarlo.')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el número de operación que figura en el comprobante.')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el CBU o alias de la cuenta desde la que transferiste.')).toBeInTheDocument();
    expect(screen.getByText('Ingresá el titular de la cuenta de origen.')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de operación')).toHaveAccessibleDescription('Ingresá el número de operación que figura en el comprobante.');
    expect(captured.calls).toBeUndefined();
  });

  it('rejectsAnOriginAccountThatIsNeitherACbuNorAnAlias', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);
    const origin = screen.getByLabelText('CBU o alias de origen');
    await user.clear(origin);
    await user.type(origin, '1234');

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Revisá el CBU (22 números) o el alias (6 a 20 caracteres).')).toBeInTheDocument();
  });

  it('sendsTheTransferDetailsAsMultipartWithAnIdempotencyKeyAndTodaysDate', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: '+ Agregar banco (opcional)' }));
    await user.type(screen.getByLabelText('Banco de origen (opcional)'), 'Banco Nación');

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await screen.findByText('Comprobante en revisión');
    const params = captured.url.searchParams;
    expect(params.getAll('invoiceIds')).toEqual(['61']);
    expect(params.get('declaredAmount')).toBe('52800.00');
    expect(params.get('currency')).toBe('ARS');
    expect(params.get('operationNumber')).toBe('4821937');
    expect(params.get('originAccount')).toBe('valentina.suarez.mp');
    expect(params.get('holderName')).toBe('Valentina Suárez');
    expect(params.get('bankName')).toBe('Banco Nación');
    expect(params.get('transferDate')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(captured.idempotencyKey).toBeTruthy();
    expect(captured.contentType).toMatch(/^multipart\/form-data/);
  });

  it('keepsTheEditedAmountWhenTheSelectionChanges', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    const amount = screen.getByLabelText('Monto transferido');
    await user.clear(amount);
    await user.type(amount, '60000');

    await user.click(screen.getByRole('button', { name: /Cuota mensual · oct 2026/ }));

    expect(amount).toHaveValue('60.000,00');
  });

  it('warnsThatTheExcessStaysAsCreditForTheNextInstallment', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    const amount = screen.getByLabelText('Monto transferido');
    await user.clear(amount);
    await user.type(amount, '60000');

    expect(screen.getByText(/Transferiste \$ 7\.200,00 de más\. Queda a tu favor y se aplica a tu próxima cuota\./)).toBeInTheDocument();
  });

  it('explainsWhichInstallmentsStayPendingWhenTheAmountOnlyCoversTheFirstOnes', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoiceId: 62 });
    await goToUploadStep(user);
    const amount = screen.getByLabelText('Monto transferido');
    await user.clear(amount);
    await user.type(amount, '70000');

    expect(screen.getByText('1 de 2 cuotas quedan en revisión. Las demás siguen pendientes.')).toBeInTheDocument();
    expect(screen.getByText('Faltan $ 35.600,00 para cubrirla')).toBeInTheDocument();
  });

  it('blocksTheSubmissionWhenTheAmountDoesNotCoverTheFirstInstallment', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user, { amount: '1000' });

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('El monto no alcanza a cubrir la primera cuota.')).toBeInTheDocument();
    expect(captured.calls).toBeUndefined();
  });

  it('confirmsOnTimeProtectionForAnInstallmentNotYetDue', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoiceId: 63 });
    await goToUploadStep(user);

    expect(screen.getByText(/Subido a tiempo: Cuota mensual · nov 2026 no genera recargo mientras se revisa\./)).toBeInTheDocument();
    expect(screen.getByText('Las cuotas ya vencidas siguen vencidas hasta que se apruebe.')).toBeInTheDocument();
  });

  it('showsTheTranslatedFieldMessageWhenTheBackendRejectsADetail', async () => {
    captureSubmission(() =>
      HttpResponse.json(
        { code: 'BANK_TRANSFER_DETAILS_REQUIRED', violations: [{ code: 'BANK_TRANSFER_OPERATION_NUMBER_REQUIRED', message: 'x' }] },
        { status: 400 },
      ),
    );
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Ingresá el número de operación que figura en el comprobante.')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de operación')).toHaveAttribute('aria-invalid', 'true');
  });

  it('showsTheDuplicateBlockWithTwoWaysOutWhenTheTransferWasAlreadyRegistered', async () => {
    captureSubmission(() => HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Ya tenemos registrada esa transferencia')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Revisar los datos' }));
    expect(screen.queryByText('Ya tenemos registrada esa transferencia')).not.toBeInTheDocument();
  });

  it('theDuplicateBlockLeadsToTheFollowUpOfTheExistingReceipt', async () => {
    captureSubmission(() => HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderTransfer({ receipts: [receiptOf({ status: 'APPROVED' })] });
    await goToUploadStep(user);
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await user.click(await screen.findByRole('button', { name: 'Ver mi comprobante' }));

    expect(await screen.findByText('Comprobante aprobado')).toBeInTheDocument();
  });

  it('asksToReviewTheOrderWhenTheBackendSaysTheOldestInstallmentsComeFirst', async () => {
    captureSubmission(() => HttpResponse.json({ code: 'RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Las cuotas se pagan en orden, desde la más vencida. Revisá la lista: puede haber cambiado.')).toBeInTheDocument();
  });

  it('reusesTheSameIdempotencyKeyWhenTheStudentRetriesWithoutChangingAnything', async () => {
    const keys = [];
    server.use(
      http.post(`${API}/me/bank-transfer-receipts`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json({ code: 'IDEMPOTENCY_KEY_CONFLICT', violations: [] }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    await screen.findByText('Ya estamos procesando este envío. Esperá un momento y revisá el seguimiento.');
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));

    expect(keys[0]).toBe(keys[1]);
  });

  it('asksForAnewIdempotencyKeyWhenTheStudentChangesAnyDetailBeforeRetrying', async () => {
    const keys = [];
    server.use(
      http.post(`${API}/me/bank-transfer-receipts`, ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'));
        return HttpResponse.json({ code: 'BANK_TRANSFER_DETAILS_REQUIRED', violations: [{ code: 'BANK_TRANSFER_OPERATION_NUMBER_REQUIRED' }] }, { status: 400 });
      }),
    );
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    await screen.findByText('Ingresá el número de operación que figura en el comprobante.');

    await user.type(screen.getByLabelText('Número de operación'), '1');
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    await vi.waitFor(() => expect(keys).toHaveLength(2));

    expect(keys[0]).not.toBe(keys[1]);
  });

  it('fallsBackToTheSingleInstallmentWhenItIsNotInTheListOfOpenInstallments', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer({ invoices: [] });
    await goToUploadStep(user);

    expect(screen.getAllByRole('button', { name: /Cuota mensual · sep 2026/ })).toHaveLength(1);
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await screen.findByText('Comprobante en revisión');
    expect(captured.url.searchParams.getAll('invoiceIds')).toEqual(['61']);
  });

  it('blocksTheSubmissionWhenTheAmountIsEmpty', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);
    await user.clear(screen.getByLabelText('Monto transferido'));

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    expect(await screen.findByText('Ingresá un monto mayor a cero.')).toBeInTheDocument();
    expect(captured.calls).toBeUndefined();
  });

  it('showsTheOrderLegendOnlyWhenThereIsMoreThanOneInstallmentToChoose', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    expect(screen.getByText(/Se pagan en orden, de la más vieja a la más nueva/)).toBeInTheDocument();
  });

  it('doesNotShowTheOrderLegendWithASingleInstallment', async () => {
    const user = userEvent.setup();
    renderTransfer({ invoices: [INSTALLMENTS[1]] });
    await goToUploadStep(user);
    expect(screen.getAllByRole('button', { name: /Cuota mensual/ })).toHaveLength(1);
    expect(screen.queryByText(/Se pagan en orden/)).not.toBeInTheDocument();
  });

  it('tellsTheStudentTheTransferDateIsTodayAndToWarnTheSecretaryOtherwise', async () => {
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);

    expect(screen.getByText(/Transferencia de hoy, \d{2}\/\d{2}\/\d{4}/)).toBeInTheDocument();
    expect(screen.getByText('Si transferiste otro día, avisale a Secretaría.')).toBeInTheDocument();
  });

  it('sendsOnlyOnceWhenTheFormIsSubmittedTwiceWhileTheRequestIsInFlight', async () => {
    let calls = 0;
    server.use(
      http.post(`${API}/me/bank-transfer-receipts`, async () => {
        calls += 1;
        await new Promise((resolve) => setTimeout(resolve, 400));
        return HttpResponse.json({ receiptId: 302 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderTransfer();
    await goToUploadStep(user);
    await fillValidForm(user);

    await user.type(screen.getByLabelText('Número de operación'), '{Enter}{Enter}');
    await vi.waitFor(() => expect(calls).toBeGreaterThan(0));
    await new Promise((resolve) => setTimeout(resolve, 100));

    expect(calls).toBe(1);
  });

  it('refreshesTheInstallmentListWhenTheBackendReportsAnOrderConflict', async () => {
    let listLoads = 0;
    captureSubmission(() => HttpResponse.json({ code: 'RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST', violations: [] }, { status: 409 }));
    const user = userEvent.setup();
    renderTransfer();
    server.use(
      http.get(`${API}/me/course-enrollments/9/invoices`, () => {
        listLoads += 1;
        return HttpResponse.json(INSTALLMENTS);
      }),
    );
    await goToUploadStep(user);
    await fillValidForm(user);
    const loadsBefore = listLoads;

    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    await screen.findByText(/Las cuotas se pagan en orden/);

    await vi.waitFor(() => expect(listLoads).toBeGreaterThan(loadsBefore));
  });

  it('usesOnlyTheInvoiceBeingPaidWhenItIsNotAmongTheOpenInstallmentsEvenIfOthersExist', async () => {
    const captured = captureSubmission();
    const user = userEvent.setup();
    renderTransfer({ invoiceId: 99 });
    await goToUploadStep(user);

    expect(screen.getAllByRole('button', { name: /Cuota mensual/ })).toHaveLength(1);
    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));

    await vi.waitFor(() => expect(captured.calls).toBe(1));
    expect(captured.url.searchParams.getAll('invoiceIds')).toEqual(['99']);
  });

  describe('follow-up per installment', () => {
    const twoInstallments = [
      { invoiceId: 61, feeType: 'MONTHLY_INSTALLMENT', period: '2026-09', totalAmount: money('52800.00'), outstandingAtSubmission: money('52800.00') },
      { invoiceId: 62, feeType: 'MONTHLY_INSTALLMENT', period: '2026-10', totalAmount: money('52800.00'), outstandingAtSubmission: money('52800.00') },
    ];

    it('showsEachInstallmentOfTheReceiptAndItsOperationNumber', async () => {
      renderTransfer({
        receipts: [receiptOf({ invoiceIds: [61, 62], invoices: twoInstallments, declaredAmount: money('105600.00') })],
      });

      expect(await screen.findByText('Comprobante en revisión')).toBeInTheDocument();
      const list = screen.getByRole('list');
      expect(within(list).getByText('Cuota mensual · sep 2026')).toBeInTheDocument();
      expect(within(list).getByText('Cuota mensual · oct 2026')).toBeInTheDocument();
      expect(within(list).getAllByText('En revisión')).toHaveLength(2);
      expect(screen.getByText('4821937')).toBeInTheDocument();
    });

    it('marksOnlyTheCoveredInstallmentsAsInReviewWhenTheTransferIsPartial', async () => {
      renderTransfer({
        receipts: [receiptOf({ invoiceIds: [61, 62], invoices: twoInstallments, declaredAmount: money('60000.00') })],
      });

      expect(await screen.findByText('Revisamos parte de tu transferencia')).toBeInTheDocument();
      const list = screen.getByRole('list');
      expect(within(list).getAllByText('En revisión')).toHaveLength(1);
      expect(within(list).getAllByText('Pendiente')).toHaveLength(1);
    });

    it('marksTheCoveredInstallmentsAsPaidWhenTheReceiptIsApproved', async () => {
      captureSubmission(() => HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }, { status: 409 }));
      const user = userEvent.setup();
      renderTransfer({
        receipts: [receiptOf({ status: 'APPROVED', invoiceIds: [61, 62], invoices: twoInstallments, declaredAmount: money('105600.00') })],
      });
      await goToUploadStep(user);
      await fillValidForm(user);
      await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
      await user.click(await screen.findByRole('button', { name: 'Ver mi comprobante' }));

      await screen.findByText('Comprobante aprobado');
      expect(within(screen.getByRole('list')).getAllByText('Pagada')).toHaveLength(2);
    });

    it('marksOnlyTheCoveredInstallmentsAsPaidWhenAnApprovedReceiptCoveredJustSome', async () => {
      captureSubmission(() => HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER', violations: [] }, { status: 409 }));
      const user = userEvent.setup();
      renderTransfer({
        receipts: [receiptOf({ status: 'APPROVED', invoiceIds: [61, 62], invoices: twoInstallments, declaredAmount: money('60000.00') })],
      });
      await goToUploadStep(user);
      await fillValidForm(user);
      await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
      await user.click(await screen.findByRole('button', { name: 'Ver mi comprobante' }));

      await screen.findByText('Comprobante aprobado');
      const list = screen.getByRole('list');
      expect(within(list).getAllByText('Pagada')).toHaveLength(1);
      expect(within(list).getAllByText('Pendiente')).toHaveLength(1);
    });

    it('tellsTheStudentToWarnTheSecretaryWhenTheRejectionIsForTheTransferDate', async () => {
      const user = userEvent.setup();
      renderTransfer({
        receipts: [receiptOf({ status: 'REJECTED', rejectionCode: 'TRANSFER_DATE_MISMATCH', rejectionReason: 'Fecha distinta' })],
      });

      expect(await screen.findByText('La fecha de la transferencia no coincide')).toBeInTheDocument();
      expect(screen.getByText(/avisale a Secretaría para que la carguen/)).toBeInTheDocument();
      expect(screen.queryByText('Fecha distinta')).not.toBeInTheDocument();
      expect(within(screen.getByRole('list')).getByText('Pendiente')).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Corregir y reenviar' }));
      expect(await screen.findByText('Datos del comprobante')).toBeInTheDocument();
    });

    it('quotesTheReviewerReasonWhenTheReviewerRejectsTheReceipt', async () => {
      renderTransfer({
        receipts: [receiptOf({ status: 'REJECTED', rejectionCode: 'REVIEWER_REJECTION', rejectionReason: 'El monto no coincide' })],
      });

      expect(await screen.findByText('Comprobante rechazado')).toBeInTheDocument();
      expect(screen.getByText('El monto no coincide')).toBeInTheDocument();
      expect(screen.getByText('Podés corregir los datos y volver a enviar el comprobante.')).toBeInTheDocument();
    });

    it('doesNotLetTheStudentJumpBackThroughTheStepperFromTheFollowUp', async () => {
      renderTransfer({ receipts: [receiptOf()] });

      await screen.findByText('Comprobante en revisión');
      expect(screen.getByRole('button', { name: /Datos bancarios/ })).toBeDisabled();
    });
  });

  describe('admission invoice', () => {
    async function submitAdmissionReceipt(application) {
      server.use(http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([application])));
      let submitted = false;
      server.use(
        http.get(`${API}/me/invoices/61/price-breakdown`, () => HttpResponse.json(breakdownOf(61, '2026-09'))),
        http.get(`${API}/me/bank-transfer-receipts`, () => HttpResponse.json(submitted ? [receiptOf({ status: 'APPROVED' })] : [])),
        http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
        http.post(`${API}/me/bank-transfer-receipts`, () => {
          submitted = true;
          return HttpResponse.json({ receiptId: 302 }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderWithProviders(
        <Routes>
          <Route path="/alumno/pagos/:invoiceId/transferencia" element={<AlumnoPagarTransferencia />} />
        </Routes>,
        { route: '/alumno/pagos/61/transferencia' },
      );
      await goToUploadStep(user);
      await fillValidForm(user);
      await user.click(screen.getByRole('button', { name: 'Enviar comprobante' }));
    }

    it('confirmsTheProgramEnrollmentWhenTheAdmissionReceiptIsApproved', async () => {
      await submitAdmissionReceipt({
        id: 3, programName: 'Diseño Gráfico', status: 'APPROVED', programEnrollmentId: 12, admissionInvoice: { id: 61, paymentStatus: 'PAID' },
      });

      expect(await screen.findByText('Tu pago fue confirmado. Ya estás inscripto en Diseño Gráfico.')).toBeInTheDocument();
    });

    it('saysTheEnrollmentIsBeingConfirmedWhenTheInvoiceIsPaidButTheEnrollmentIsNotCreatedYet', async () => {
      await submitAdmissionReceipt({
        id: 3, programName: 'Diseño Gráfico', status: 'APPROVED', programEnrollmentId: null, admissionInvoice: { id: 61, paymentStatus: 'PAID' },
      });

      expect(await screen.findByText('Tu pago fue confirmado. Estamos confirmando tu inscripción en Diseño Gráfico.')).toBeInTheDocument();
      expect(screen.queryByText(/Ya estás inscripto/)).not.toBeInTheDocument();
    });

    it('doesNotSayTheStudentIsEnrolledWhenTheAdmissionInvoiceIsStillUnpaid', async () => {
      await submitAdmissionReceipt({
        id: 3, programName: 'Diseño Gráfico', status: 'APPROVED', programEnrollmentId: null, admissionInvoice: { id: 61, paymentStatus: 'PENDING' },
      });

      expect(await screen.findByText(/todavía no alcanza a cubrir la matrícula de ingreso completa/)).toBeInTheDocument();
      expect(screen.queryByText(/inscripto/)).not.toBeInTheDocument();
    });
  });

  it('describesEachTrackedInstallmentRowWithItsOwnStatusNote', async () => {
    renderTransfer({ receipts: [receiptOf()] });

    const row = (await screen.findByText('Tus cuotas')).closest('div').querySelector('li');
    const noteId = row.getAttribute('aria-describedby');
    expect(noteId).toBe('tracking-note-61');
    expect(document.getElementById(noteId)).toHaveTextContent('Incluida en tu comprobante en revisión');
  });

  it('scrollsTheStepperIntoViewWhenTheStudentMovesToAnotherStep', async () => {
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    const user = userEvent.setup();
    renderTransfer();
    await user.click(await screen.findByRole('button', { name: 'Ya transferí — subir comprobante' }));

    await waitFor(() => expect(scrollIntoView).toHaveBeenCalledTimes(1));
    delete Element.prototype.scrollIntoView;
  });
});
