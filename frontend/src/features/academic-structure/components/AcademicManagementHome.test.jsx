import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { AdministrativoSubjects } from '@/features/academic-structure/pages/AdministrativoSubjects';
import { subjectFixture } from '@/test/fixtures/subjects';
import { AcademicManagementHome } from './AcademicManagementHome';

const API = 'http://localhost:8080';
const ROUTE = '/administrativo/gestion-academica';

function program(overrides) {
  return { id: 1, name: 'Carrera', status: 'ACTIVE', visibleInCatalog: false, ...overrides };
}

function cohort(overrides) {
  return {
    id: 1,
    intake: 'FIRST',
    admissionStart: '2026-09-01',
    admissionEnd: '2026-08-31',
    cursadaStart: '2026-03-01',
    ...overrides,
  };
}

function mockManagementData({ programs = [], courses = [], subjects = [], cohortsByProgram = {} }) {
  server.use(
    http.get(`${API}/programs`, () => HttpResponse.json(programs)),
    http.get(`${API}/courses`, () => HttpResponse.json(courses)),
    http.get(`${API}/subjects`, () => HttpResponse.json(subjects)),
    http.get(`${API}/programs/:id/cohorts`, ({ params }) => HttpResponse.json(cohortsByProgram[params.id] ?? [])),
  );
}

function renderHome(props = {}) {
  return renderWithProviders(<AcademicManagementHome onViewAllPublishedPrograms={() => {}} {...props} />, {
    route: ROUTE,
  });
}

function kpiCard(label) {
  return screen.getByText(label).parentElement;
}

describe('AcademicManagementHome', () => {
  installFixedClock({ advanceWithRealTime: true });

  it('shows the four counters from the existing endpoints and only counts admissions ending today or later', async () => {
    mockManagementData({
      programs: [program({ id: 1, name: 'Licenciatura' }), program({ id: 2, name: 'Tecnicatura' }), program({ id: 3, status: 'INACTIVE' })],
      courses: [{ id: 1, status: 'ACTIVE' }, { id: 2, status: 'ACTIVE' }, { id: 3, status: 'INACTIVE' }],
      subjects: [1, 2, 3, 4, 5].map((id) => subjectFixture({ id, code: `S${id}` })),
      cohortsByProgram: {
        1: [cohort({ id: 10, admissionEnd: '2026-11-05' }), cohort({ id: 11, admissionEnd: '2026-11-04' })],
        2: [cohort({ id: 12, admissionEnd: '2027-02-01' })],
      },
    });
    renderHome();

    await screen.findByText('Carreras activas');
    expect(within(kpiCard('Carreras activas')).getByText('2')).toBeInTheDocument();
    expect(within(kpiCard('Cursos activos')).getByText('2')).toBeInTheDocument();
    expect(within(kpiCard('Materias en el banco')).getByText('5')).toBeInTheDocument();
    expect(within(kpiCard('Cohortes abiertas')).getByText('2')).toBeInTheDocument();
  });

  it('highlights open cohorts only when there is at least one', async () => {
    mockManagementData({
      programs: [program({ id: 1 })],
      cohortsByProgram: { 1: [cohort({ admissionEnd: '2026-12-01' })] },
    });
    const { unmount } = renderHome();
    await screen.findByText('Cohortes abiertas');
    const highlighted = await within(kpiCard('Cohortes abiertas')).findByText('1');
    expect(highlighted).toHaveClass('text-brand');
    unmount();

    mockManagementData({ programs: [program({ id: 1 })], cohortsByProgram: { 1: [] } });
    renderHome();
    await screen.findByText('Cohortes abiertas');
    const plain = within(kpiCard('Cohortes abiertas')).getByText('0');
    expect(plain).not.toHaveClass('text-brand');
  });

  it('warns about published programs without open cohorts and opens the cohort dialog for that program', async () => {
    mockManagementData({
      programs: [
        program({ id: 1, name: 'Licenciatura en Administración', visibleInCatalog: true }),
        program({ id: 2, name: 'Tecnicatura en Seguridad', visibleInCatalog: true }),
        program({ id: 3, name: 'Borrador sin publicar', visibleInCatalog: false }),
      ],
      cohortsByProgram: {
        1: [cohort({ admissionEnd: '2026-10-01' })],
        2: [cohort({ id: 20, admissionEnd: '2026-12-15' })],
      },
    });
    const requestedTemplates = [];
    server.use(
      http.get(`${API}/programs/:id/year-billing-templates`, ({ params }) => {
        requestedTemplates.push(params.id);
        return HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderHome();

    const alert = await screen.findByRole('status');
    expect(alert).toHaveTextContent('Licenciatura en Administración está publicada pero no tiene cohortes de ingreso abiertas.');
    expect(screen.queryByText(/Tecnicatura en Seguridad está publicada/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Borrador sin publicar está publicada/)).not.toBeInTheDocument();

    await user.click(within(alert).getByRole('button', { name: 'Agregar cohorte' }));

    expect(await screen.findByRole('dialog', { name: 'Nueva cohorte de ingreso' })).toBeInTheDocument();
    await waitFor(() => expect(requestedTemplates).toContain('1'));
  });

  it('does not warn when every published program has an open cohort', async () => {
    mockManagementData({
      programs: [program({ id: 1, visibleInCatalog: true })],
      cohortsByProgram: { 1: [cohort({ admissionEnd: '2026-12-15' })] },
    });
    renderHome();

    await screen.findByText('Próximos eventos');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('lists only events inside the next eight weeks in ascending order linking to the cohorts tab', async () => {
    mockManagementData({
      programs: [program({ id: 7, name: 'Licenciatura' }), program({ id: 8, name: 'Tecnicatura' })],
      cohortsByProgram: {
        7: [
          cohort({ id: 1, admissionEnd: '2026-12-21', cursadaStart: '2027-03-02' }),
          cohort({ id: 2, admissionEnd: '2026-11-04', cursadaStart: '2026-12-31' }),
        ],
        8: [
          cohort({ id: 3, admissionEnd: '2026-11-16', cursadaStart: '2027-01-01' }),
          cohort({ id: 4, admissionEnd: '2026-11-05', cursadaStart: '2027-06-01' }),
        ],
      },
    });
    renderHome();

    const items = within(await screen.findByRole('list')).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('05NOV'),
      expect.stringContaining('16NOV'),
      expect.stringContaining('21DIC'),
      expect.stringContaining('31DIC'),
    ]);
    expect(items[1]).toHaveTextContent('Cierre de admisión');
    expect(items[1]).toHaveTextContent('Tecnicatura');
    expect(items[3]).toHaveTextContent('Inicio de cursada');
    expect(within(items[1]).getByRole('link')).toHaveAttribute('href', `${ROUTE}/carreras/8?tab=cohortes`);
    expect(within(items[2]).getByRole('link')).toHaveAttribute('href', `${ROUTE}/carreras/7?tab=cohortes`);
  });

  it('shows at most eight events and reveals the rest with Ver todo', async () => {
    const cohorts = Array.from({ length: 10 }, (_, index) =>
      cohort({ id: index + 1, admissionEnd: `2026-11-${String(10 + index).padStart(2, '0')}`, cursadaStart: '2027-06-01' }),
    );
    mockManagementData({ programs: [program({ id: 1, name: 'Licenciatura' })], cohortsByProgram: { 1: cohorts } });
    const user = userEvent.setup();
    renderHome();

    expect(await within(await screen.findByRole('list')).findAllByRole('listitem')).toHaveLength(8);
    await user.click(screen.getByRole('button', { name: /Ver todo/ }));

    expect(within(screen.getByRole('list')).getAllByRole('listitem')).toHaveLength(10);
    expect(screen.queryByRole('button', { name: /Ver todo/ })).not.toBeInTheDocument();
  });

  it('does not offer Ver todo when all events fit', async () => {
    mockManagementData({
      programs: [program({ id: 1 })],
      cohortsByProgram: { 1: [cohort({ admissionEnd: '2026-11-10', cursadaStart: '2026-12-01' })] },
    });
    renderHome();

    await screen.findByRole('list');
    expect(screen.queryByRole('button', { name: /Ver todo/ })).not.toBeInTheDocument();
  });

  it('shows a clear empty message when no events fall in the next two months', async () => {
    mockManagementData({
      programs: [program({ id: 1 })],
      cohortsByProgram: { 1: [cohort({ admissionEnd: '2027-03-01', cursadaStart: '2027-04-01' })] },
    });
    renderHome();

    expect(await screen.findByText('No hay eventos programados en los próximos 2 meses.')).toBeInTheDocument();
  });

  it('renders a complete screen with zeros and empty messages for a brand new institute', async () => {
    mockManagementData({});
    renderHome();

    expect(await screen.findByText('Todavía no cargaste carreras')).toBeInTheDocument();
    expect(screen.getByText('Todavía no cargaste cursos')).toBeInTheDocument();
    expect(screen.getByText('El banco está vacío')).toBeInTheDocument();
    expect(screen.getByText('Sin cohortes')).toBeInTheDocument();
    expect(screen.getByText('No hay eventos programados en los próximos 2 meses.')).toBeInTheDocument();
    expect(screen.getByText('Todavía no hay carreras publicadas.')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nueva cohorte' })).toBeDisabled();
  });

  it('shows a skeleton while the summary loads', async () => {
    mockManagementData({});
    server.use(http.get(`${API}/programs`, async () => new Promise(() => {})));
    renderHome();

    expect(await screen.findByTestId('management-home-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('Carreras activas')).not.toBeInTheDocument();
  });

  it('shows a retryable error and recovers when retrying', async () => {
    let failing = true;
    mockManagementData({});
    server.use(
      http.get(`${API}/courses`, () => (failing ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]))),
    );
    const user = userEvent.setup();
    renderHome();

    expect(await screen.findByText('No pudimos cargar el resumen')).toBeInTheDocument();
    failing = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Carreras activas')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar el resumen')).not.toBeInTheDocument();
  });

  it('keeps the section tabs available when the summary fails', async () => {
    mockManagementData({});
    server.use(http.get(`${API}/courses`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<AdministrativoSubjects />, { route: ROUTE });

    expect(await screen.findByText('No pudimos cargar el resumen')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Banco de materias' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Oferta académica' })).toBeInTheDocument();
  });

  it('picks a program before opening the cohort dialog from Nueva cohorte', async () => {
    mockManagementData({
      programs: [program({ id: 4, name: 'Licenciatura' }), program({ id: 5, name: 'Tecnicatura' })],
    });
    const requestedTemplates = [];
    server.use(
      http.get(`${API}/programs/:id/year-billing-templates`, ({ params }) => {
        requestedTemplates.push(params.id);
        return HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderHome();

    await user.click(await screen.findByRole('button', { name: 'Nueva cohorte' }));
    const picker = await screen.findByRole('dialog', { name: 'Nueva cohorte de ingreso' });
    expect(within(picker).getByRole('button', { name: 'Continuar' })).toBeDisabled();
    await user.selectOptions(within(picker).getByLabelText('Carrera'), 'Tecnicatura');
    await user.click(within(picker).getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByText('Año lectivo')).toBeInTheDocument();
    await waitFor(() => expect(requestedTemplates).toEqual(['5']));
  });

  it('lists the three most recently published programs and links to the offer tab', async () => {
    mockManagementData({
      programs: [
        program({ id: 1, name: 'Antigua', visibleInCatalog: true }),
        program({ id: 2, name: 'Segunda', visibleInCatalog: true }),
        program({ id: 3, name: 'Tercera', visibleInCatalog: true }),
        program({ id: 4, name: 'Cuarta', visibleInCatalog: true }),
        program({ id: 5, name: 'No publicada', visibleInCatalog: false }),
      ],
      cohortsByProgram: { 1: [cohort({ admissionEnd: '2027-01-01' })], 2: [cohort({ admissionEnd: '2027-01-01' })], 3: [cohort({ admissionEnd: '2027-01-01' })], 4: [cohort({ admissionEnd: '2027-01-01' })] },
    });
    const user = userEvent.setup();
    renderWithProviders(<AdministrativoSubjects />, { route: ROUTE });

    const recentHeading = await screen.findByRole('heading', { name: 'Carreras publicadas recientemente' });
    const recentPanel = recentHeading.parentElement;
    expect(within(recentPanel).getAllByRole('listitem').map((item) => item.textContent)).toEqual(['Cuarta', 'Tercera', 'Segunda']);

    await user.click(within(recentPanel).getByRole('button', { name: /Ver todas las carreras publicadas/ }));

    expect(await screen.findByRole('tab', { name: 'Cursos publicados' })).toBeInTheDocument();
  });
});
