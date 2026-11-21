import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { ownGraduationCertificate } from '@/test/fixtures/certificates';
import { revokedOwnCertificatePdfHandler, unavailableOwnCertificatePdfHandler } from '@/test/mocks/certificateErrorHandlers';
import { routes } from '@/features/graduation/routes';
import { MyCertificates } from '@/features/graduation/certificates/pages/MyCertificates';

const API = 'http://localhost:8080';

const validCertificate = ownGraduationCertificate({ id: 101, titleName: 'Tecnicatura en Administración' });
const revokedCertificate = ownGraduationCertificate({
  id: 102,
  kind: 'COURSE',
  titleName: 'Curso de Excel',
  status: 'REVOKED',
  revokedAt: '2026-11-21T09:00:00',
  downloadable: false,
  previewable: false,
  registryNumber: '0000413/2026',
});

function listReturns(certificates) {
  server.use(http.get(`${API}/me/certificates`, () => HttpResponse.json(certificates)));
}

describe('MyCertificates', () => {
  let clickedLinks;

  beforeEach(() => {
    clickedLinks = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function recordClick() {
      clickedLinks.push({ download: this.download, href: this.href });
    });
    URL.createObjectURL = vi.fn(() => 'blob:certificate');
    URL.revokeObjectURL = vi.fn();
  });

  it('is reachable only by the student role', () => {
    const route = routes.role.find((entry) => entry.path === 'mis-certificados');
    expect(route.roles).toEqual(['alumno']);
  });

  it('lists each certificate with its kind, status, dates and registry number', async () => {
    listReturns([validCertificate, revokedCertificate]);
    renderWithProviders(<MyCertificates />);

    expect(await screen.findByRole('heading', { name: 'Tecnicatura en Administración' })).toBeInTheDocument();
    expect(screen.getByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('Revocado')).toBeInTheDocument();
    expect(screen.getByText('Carrera')).toBeInTheDocument();
    expect(screen.getByText('Curso')).toBeInTheDocument();
    expect(screen.getByText('0000412/2026')).toBeInTheDocument();
    expect(screen.getByText('0000413/2026')).toBeInTheDocument();
  });

  it('shows a loading state while the list is fetched', () => {
    renderWithProviders(<MyCertificates />);
    expect(screen.getByLabelText('Cargando certificados')).toBeInTheDocument();
  });

  it('shows the empty state when the student has no certificates', async () => {
    listReturns([]);
    renderWithProviders(<MyCertificates />);
    expect(await screen.findByText('Todavía no tenés certificados')).toBeInTheDocument();
  });

  it('shows an error with a retry that reloads the list', async () => {
    const user = userEvent.setup({ delay: null });
    server.use(http.get(`${API}/me/certificates`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })));
    renderWithProviders(<MyCertificates />);

    expect(await screen.findByText('No pudimos cargar tus certificados')).toBeInTheDocument();
    listReturns([validCertificate]);
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('heading', { name: 'Tecnicatura en Administración' })).toBeInTheDocument();
  });

  it('offers preview and download on a valid certificate', async () => {
    listReturns([validCertificate]);
    renderWithProviders(<MyCertificates />);
    expect(await screen.findByRole('button', { name: 'Descargar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vista previa' })).toBeInTheDocument();
  });

  it('shows neither action on a revoked certificate', async () => {
    listReturns([revokedCertificate]);
    renderWithProviders(<MyCertificates />);
    expect(await screen.findByText('Revocado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Descargar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Vista previa' })).not.toBeInTheDocument();
  });

  it('follows the contract flags instead of the status when choosing buttons', async () => {
    listReturns([ownGraduationCertificate({ previewable: false, downloadable: true })]);
    renderWithProviders(<MyCertificates />);
    expect(await screen.findByRole('button', { name: 'Descargar' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Vista previa' })).not.toBeInTheDocument();
  });

  it('downloads the PDF through an object URL that is released', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    server.use(
      http.get(`${API}/me/certificates/:id/pdf`, () =>
        new HttpResponse('%PDF-1.4', {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="certificado.pdf"' },
        }),
      ),
    );
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Descargar' }));
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled());
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:certificate');
    expect(clickedLinks).toHaveLength(1);
    expect(clickedLinks[0].download).toBe('certificado.pdf');
    expect(clickedLinks[0].href).toBe('blob:certificate');
  });

  it('opens the preview in a dialog and releases the object URL when it closes', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));

    expect(await screen.findByTitle('Vista previa del certificado de Tecnicatura en Administración')).toHaveAttribute(
      'src',
      'blob:certificate',
    );
    await user.keyboard('{Escape}');
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:certificate'));
  });

  it('shows the translated message when the PDF was revoked in the meantime', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    server.use(revokedOwnCertificatePdfHandler);
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Descargar' }));
    expect(await screen.findByText('El certificado fue revocado y ya no se puede descargar.')).toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it('refreshes the list after the PDF fails', async () => {
    const user = userEvent.setup({ delay: null });
    let listRequests = 0;
    server.use(
      http.get(`${API}/me/certificates`, () => {
        listRequests += 1;
        return HttpResponse.json([validCertificate]);
      }),
      revokedOwnCertificatePdfHandler,
    );
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Descargar' }));
    await screen.findByText('El certificado fue revocado y ya no se puede descargar.');
    await waitFor(() => expect(listRequests).toBe(2));
  });

  it('shows the translated message when the certificate is not issued yet', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    server.use(unavailableOwnCertificatePdfHandler);
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Descargar' }));
    expect(await screen.findByText('El certificado todavía no fue emitido.')).toBeInTheDocument();
  });

  it('clears the previous error when a new preview opens and when it closes', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    server.use(revokedOwnCertificatePdfHandler);
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));
    await screen.findByRole('alert');

    server.resetHandlers();
    listReturns([validCertificate]);
    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));
    await screen.findByTitle('Vista previa del certificado de Tecnicatura en Administración');
    expect(screen.queryByText('El certificado fue revocado y ya no se puede descargar.')).not.toBeInTheDocument();
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByTitle('Vista previa del certificado de Tecnicatura en Administración')).not.toBeInTheDocument());
    expect(screen.queryByText('El certificado fue revocado y ya no se puede descargar.')).not.toBeInTheDocument();
  });

  it('drops the stale PDF error when the list is retried after failing to refresh', async () => {
    const user = userEvent.setup({ delay: null });
    let listRequests = 0;
    server.use(
      http.get(`${API}/me/certificates`, () => {
        listRequests += 1;
        if (listRequests === 1) return HttpResponse.json([validCertificate]);
        if (listRequests === 2) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 });
        return HttpResponse.json([validCertificate]);
      }),
      revokedOwnCertificatePdfHandler,
    );
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Descargar' }));
    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Tecnicatura en Administración' })).toBeInTheDocument();
    expect(screen.queryByText('El certificado fue revocado y ya no se puede descargar.')).not.toBeInTheDocument();
  });

  it('shows the translated message when the certificate is not found', async () => {
    const user = userEvent.setup({ delay: null });
    listReturns([validCertificate]);
    server.use(
      http.get(`${API}/me/certificates/:id/pdf`, () =>
        HttpResponse.json(errorBody('GRADUATION_CERTIFICATE_NOT_FOUND'), { status: 404 }),
      ),
    );
    renderWithProviders(<MyCertificates />);
    await user.click(await screen.findByRole('button', { name: 'Vista previa' }));
    expect(await screen.findByText('No encontramos el certificado de egreso.')).toBeInTheDocument();
  });

  it('has no accessibility violations with valid and revoked certificates', async () => {
    listReturns([validCertificate, revokedCertificate]);
    const { container } = renderWithProviders(<MyCertificates />);
    await screen.findByText('Revocado');
    expect(await axe(container)).toHaveNoViolations();
  });
});
