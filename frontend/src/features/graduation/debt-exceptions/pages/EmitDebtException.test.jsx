import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { toast } from 'sonner';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { debtException } from '@/test/fixtures/constancias';
import { axe } from '@/test/axe';
import { EmitDebtException } from '@/features/graduation/debt-exceptions/pages/EmitDebtException';
import { saveBlob } from '@/features/graduation/certificates/lib/saveBlob';

const API = 'http://localhost:8080';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/graduation/certificates/lib/saveBlob', () => ({ saveBlob: vi.fn() }));

const student = {
  id: 3,
  personId: 103,
  apellido: 'Paz',
  nombre: 'Ana',
  dni: '38512774',
  username: '38512774',
  email: 'ana@demo.test',
  emailVerified: true,
  status: 'ACTIVE',
  onboardingStatus: 'CONFIRMADO',
};
const REASON_OK = 'Cuota abonada en efectivo pendiente';

let issueBody;

function mockStudents(items = [student]) {
  server.use(http.get(`${API}/students`, () => HttpResponse.json({ items, page: 0, size: 8, totalElements: items.length })));
}

function mockIssue(response = () => HttpResponse.json(debtException(), { status: 201 })) {
  server.use(
    http.post(`${API}/document-debt-exceptions`, async ({ request }) => {
      issueBody = await request.json();
      return response();
    }),
  );
}

function renderScreen() {
  return renderWithProviders(<EmitDebtException />, { route: '/administrativo/constancias-por-excepcion' });
}

async function pickStudent() {
  await userEvent.type(screen.getByLabelText('Alumno'), 'Paz');
  await userEvent.click(await screen.findByRole('button', { name: /Elegir a Paz, Ana/ }));
}

async function fillReason(text) {
  const field = screen.getByLabelText(/Motivo/);
  await userEvent.clear(field);
  await userEvent.type(field, text);
}

const submitButton = () => screen.getByRole('button', { name: 'Emitir por excepción' });

beforeEach(() => {
  issueBody = undefined;
  vi.clearAllMocks();
  mockStudents();
});

describe('EmitDebtException', () => {
  it('searchesStudentsBySurnameOrDniAndShowsTheChoice', async () => {
    const seen = [];
    server.use(
      http.get(`${API}/students`, ({ request }) => {
        seen.push(new URL(request.url).searchParams.get('search'));
        return HttpResponse.json({ items: [student], page: 0, size: 8, totalElements: 1 });
      }),
    );
    renderScreen();

    await pickStudent();

    expect(seen).toContain('Paz');
    expect(screen.getByText('Paz, Ana')).toBeInTheDocument();
    expect(screen.getByText(/DNI 38512774/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar alumno' }));
    expect(screen.getByLabelText('Alumno')).toBeInTheDocument();
  });

  it('tellsWhenNoStudentMatches', async () => {
    mockStudents([]);
    renderScreen();

    await userEvent.type(screen.getByLabelText('Alumno'), 'Zzz');

    expect(await screen.findByText('No encontramos alumnos con ese apellido o DNI.')).toBeInTheDocument();
  });

  it('keepsSubmitDisabledWithNineteenCharactersAndEnablesItWithTwenty', async () => {
    renderScreen();
    await pickStudent();

    await fillReason('a'.repeat(19));
    expect(screen.getByText('19/20')).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();

    await userEvent.type(screen.getByLabelText(/Motivo/), 'a');
    expect(screen.getByText('20/20')).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('keepsSubmitDisabledWithoutAStudent', async () => {
    renderScreen();

    await fillReason(REASON_OK);

    expect(submitButton()).toBeDisabled();
  });

  it.each(['0', '91', 'abc', ''])('rejectsValidityOf%s', async (text) => {
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    const days = screen.getByLabelText(/Vence en/);
    await userEvent.clear(days);
    if (text) await userEvent.type(days, text);
    await userEvent.tab();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();
  });

  it.each(['1', '90'])('acceptsValidityOf%s', async (text) => {
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    const days = screen.getByLabelText(/Vence en/);
    await userEvent.clear(days);
    await userEvent.type(days, text);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('fillsValidityFromTheQuickOptions', async () => {
    renderScreen();

    await userEvent.click(screen.getByRole('button', { name: '15 días' }));

    expect(screen.getByLabelText(/Vence en/)).toHaveValue('15');
    expect(screen.getByRole('button', { name: '15 días' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('issuesTheExceptionShowsEmitidaAndDownloadsThePdf', async () => {
    mockIssue();
    let pdfRequested = false;
    server.use(
      http.get(`${API}/document-debt-exceptions/:id/pdf`, ({ params }) => {
        pdfRequested = params.id === '5';
        return new HttpResponse(new Uint8Array([0x25, 0x50, 0x44, 0x46]), {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="constancia-ABC.pdf"' },
        });
      }),
    );
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(screen.getByRole('button', { name: '15 días' }));

    await userEvent.click(submitButton());

    expect(await screen.findByText('Constancia emitida por excepción.')).toBeInTheDocument();
    expect(issueBody).toEqual({ studentId: 3, documentType: 'REGULAR_STUDENT_CERTIFICATE', reason: REASON_OK, validityDays: 15 });
    expect(screen.getByText(/Se puede usar una sola vez/)).toBeInTheDocument();
    expect(screen.getByText(/Auditoría/)).toBeInTheDocument();
    expect(screen.queryByText(/Trámites del alumno/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Descargar para entregar' }));

    await waitFor(() => expect(saveBlob).toHaveBeenCalledTimes(1));
    expect(saveBlob.mock.calls[0][0].type).toBe('application/pdf');
    expect(saveBlob.mock.calls[0][1]).toBe('constancia-ABC.pdf');
    expect(pdfRequested).toBe(true);
    expect(screen.getByRole('button', { name: 'Descargada' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Emitir otra' })).toBeEnabled();
  });

  it('startsAgainWithEmitirOtra', async () => {
    mockIssue();
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());
    await screen.findByText('Constancia emitida por excepción.');

    await userEvent.click(screen.getByRole('button', { name: 'Emitir otra' }));

    expect(screen.getByLabelText('Alumno')).toBeInTheDocument();
    expect(screen.getByLabelText(/Motivo/)).toHaveValue('');
    expect(submitButton()).toBeDisabled();
  });

  it('sendsTheChosenDocumentType', async () => {
    mockIssue();
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    await userEvent.click(screen.getByRole('combobox', { name: 'Constancia' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Certificado de egreso' }));
    await userEvent.click(submitButton());

    await screen.findByText('Constancia emitida por excepción.');
    expect(issueBody.documentType).toBe('GRADUATION_CERTIFICATE');
  });

  it('showsTheNoPermissionStateWhenIssuingIsForbidden', async () => {
    mockIssue(() => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 }));
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    await userEvent.click(submitButton());

    expect(await screen.findByText('No tenés permiso para emitir por excepción.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Emitir por excepción' })).not.toBeInTheDocument();
  });

  it('showsTheServerErrorAndKeepsTheFormWhenIssuingFails', async () => {
    mockIssue(() => HttpResponse.json(errorBody('DOCUMENT_OPERATION_PENDING'), { status: 409 }));
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    await userEvent.click(submitButton());

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Esta operación de documentos todavía no está disponible.'));
    expect(screen.getByLabelText(/Motivo/)).toHaveValue(REASON_OK);
  });

  it.each([
    [409, 'DEBT_EXCEPTION_ALREADY_USED', 'Esta constancia ya se descargó y no se puede descargar de nuevo.'],
    [409, 'DEBT_EXCEPTION_EXPIRED', 'La excepción venció.'],
    [404, 'DEBT_EXCEPTION_NOT_FOUND', 'No encontramos la excepción.'],
  ])('mapsTheDownloadError%s%s', async (status, code, message) => {
    mockIssue();
    server.use(http.get(`${API}/document-debt-exceptions/:id/pdf`, () => HttpResponse.json(errorBody(code), { status })));
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());
    await screen.findByText('Constancia emitida por excepción.');

    await userEvent.click(screen.getByRole('button', { name: 'Descargar para entregar' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(message));
    expect(saveBlob).not.toHaveBeenCalled();
  });

  it('sendsASingleRequestWhenSubmittedTwiceInTheSameTick', async () => {
    let posts = 0;
    server.use(
      http.post(`${API}/document-debt-exceptions`, async () => {
        posts += 1;
        await new Promise((resolve) => setTimeout(resolve, 50));
        return HttpResponse.json(debtException(), { status: 201 });
      }),
    );
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);

    const form = submitButton().closest('form');
    fireEvent.submit(form);
    fireEvent.submit(form);

    await screen.findByText('Constancia emitida por excepción.');
    expect(posts).toBe(1);
  });

  it('movesFocusToTheIssuedNoticeAnnouncedAsStatus', async () => {
    mockIssue();
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('Constancia emitida por excepción.');
    expect(status.parentElement).toHaveFocus();
  });

  it('showsTheNoPermissionStateWhenTheDownloadIsForbidden', async () => {
    mockIssue();
    server.use(http.get(`${API}/document-debt-exceptions/:id/pdf`, () => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 })));
    renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());
    await screen.findByText('Constancia emitida por excepción.');

    await userEvent.click(screen.getByRole('button', { name: 'Descargar para entregar' }));

    expect(await screen.findByText('No tenés permiso para emitir por excepción.')).toBeInTheDocument();
  });

  it('turnsPastedLineBreaksIntoSpaces', async () => {
    renderScreen();

    await userEvent.click(screen.getByLabelText(/Motivo/));
    await userEvent.paste('primera línea\nsegunda línea\r\ntercera');

    expect(screen.getByLabelText(/Motivo/)).toHaveValue('primera línea segunda línea tercera');
  });

  it('hasNoAccessibilityViolationsWhenIssued', async () => {
    mockIssue();
    const { container } = renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());
    await screen.findByText('Constancia emitida por excepción.');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('hasNoAccessibilityViolationsWithoutPermission', async () => {
    mockIssue(() => HttpResponse.json(errorBody('FORBIDDEN'), { status: 403 }));
    const { container } = renderScreen();
    await pickStudent();
    await fillReason(REASON_OK);
    await userEvent.click(submitButton());
    await screen.findByText('No tenés permiso para emitir por excepción.');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('hasNoAccessibilityViolationsInTheForm', async () => {
    const { container } = renderScreen();

    expect(await axe(container)).toHaveNoViolations();
  });
});
