import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { ThemeProvider } from '@/context/ThemeContext';
import { TopNav } from '@/components/TopNav';
import { groupMenuItems, menuItemsFor } from '@/routes/roleMenu';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 7, nombre: 'Ana', apellido: 'Pérez', roles: ['ALUMNO'] }, roles: ['ALUMNO'], logout: vi.fn() }),
}));

afterEach(() => vi.useRealTimers());

const API = 'http://localhost:8080';

const designBachelor = { id: 1, name: 'Licenciatura en Diseño Gráfico', area: 'DESIGN', level: 'BACHELOR', totalYears: 4, teachingMode: 'IN_PERSON' };
const businessBachelor = { id: 2, name: 'Licenciatura en Administración', area: 'BUSINESS_ADMINISTRATION', level: 'BACHELOR', totalYears: 4, teachingMode: 'IN_PERSON' };
const designUndergraduate = { id: 3, name: 'Tecnicatura en Diseño de Interiores', area: 'DESIGN', level: 'UNDERGRADUATE', totalYears: 3, teachingMode: 'IN_PERSON' };
const photographyCourse = { id: 7, name: 'Fotografía digital', area: 'DESIGN', durationMonths: 3, teachingMode: 'IN_PERSON' };
const marketingCourse = { id: 8, name: 'Marketing digital', area: 'BUSINESS_ADMINISTRATION', durationMonths: 2, teachingMode: 'ONLINE' };

function respondWith(programs, courses) {
  const requests = [];
  server.use(
    http.get(`${API}/programs`, ({ request }) => {
      requests.push(request.url);
      return HttpResponse.json(programs);
    }),
    http.get(`${API}/courses`, ({ request }) => {
      requests.push(request.url);
      return HttpResponse.json(courses);
    }),
  );
  return requests;
}

function respondWithSample() {
  return respondWith([designBachelor, businessBachelor, designUndergraduate], [photographyCourse, marketingCourse]);
}

function renderBar() {
  const items = menuItemsFor('alumno');
  return renderWithProviders(
    <ThemeProvider>
      <TopNav titulo="Alumno" items={items} groups={groupMenuItems('alumno', items)} />
    </ThemeProvider>,
    { route: '/alumno/inicio' },
  );
}

const trigger = () => screen.getByRole('button', { name: 'Oferta académica' });
const row = (name) => screen.getByRole('button', { name: new RegExp(`^${name}`) });

async function openPanel(user) {
  await user.click(trigger());
  await screen.findByRole('button', { name: /^Diseño/ });
  await screen.findByText('(3)');
}

describe('TopNavCatalogPanel', () => {
  it('showsTheThreeZonesWithCountsAndAHintUntilSomethingIsPointed', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();

    await openPanel(user);

    expect(screen.getByText('Áreas académicas')).toBeInTheDocument();
    expect(screen.getByText('Tipo de oferta')).toBeInTheDocument();
    expect(row('Diseño')).toHaveTextContent('(3)');
    expect(row('Administración y Negocios')).toHaveTextContent('(2)');
    expect(screen.queryByRole('button', { name: /^Salud/ })).not.toBeInTheDocument();
    expect(row('De grado')).toHaveTextContent('(2)');
    expect(row('Cursos')).toHaveTextContent('(2)');
    expect(row('Posgrado')).toHaveTextContent('(0)');
    expect(screen.getByText('Elegí un área o un tipo para ver la oferta.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver toda la oferta' })).toHaveAttribute('href', '/catalogo');
  });

  it('asksForThePublishedOfferingOnceWhateverTheMouseDoes', async () => {
    const requests = respondWithSample();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);

    await user.hover(row('Diseño'));
    act(() => vi.advanceTimersByTime(200));
    await user.hover(row('De grado'));
    act(() => vi.advanceTimersByTime(200));

    expect(requests).toHaveLength(2);
    expect(requests.every((url) => url.includes('status=ACTIVE') && url.includes('visibleInCatalog=true'))).toBe(true);
  });

  it('switchesTheListAfter140msAndShowsOnlyTheAreaPointed', async () => {
    respondWithSample();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);

    await user.hover(row('Diseño'));
    act(() => vi.advanceTimersByTime(100));
    expect(screen.queryByRole('link', { name: /Fotografía digital/ })).not.toBeInTheDocument();
    act(() => vi.advanceTimersByTime(100));

    const list = screen.getByRole('list', { name: 'Oferta de Diseño' });
    expect(within(list).getAllByRole('link')).toHaveLength(3);
    expect(within(list).queryByRole('link', { name: /Administración/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver todo en Diseño' })).toHaveAttribute('href', '/catalogo?area=DESIGN');
  });

  it('showsOneAxisAtATimeAndNeverCombinesAreaWithType', async () => {
    respondWithSample();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);

    await user.hover(row('Diseño'));
    act(() => vi.advanceTimersByTime(200));
    await user.hover(row('De grado'));
    act(() => vi.advanceTimersByTime(200));

    const list = screen.getByRole('list', { name: 'Carreras de grado' });
    expect(within(list).getByRole('link', { name: /Licenciatura en Administración/ })).toBeInTheDocument();
    expect(within(list).getByRole('link', { name: /Licenciatura en Diseño Gráfico/ })).toBeInTheDocument();
    expect(within(list).queryByRole('link', { name: /Tecnicatura/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Oferta de Diseño' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver todo en carreras de grado' })).toHaveAttribute('href', '/catalogo?level=BACHELOR');
  });

  it('labelsEveryItemWithItsTypeAndAreaAndNeverShowsPrices', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);

    await user.click(row('Cursos'));

    const item = screen.getByRole('link', { name: /Fotografía digital/ });
    expect(item).toHaveTextContent('Curso');
    expect(item).toHaveTextContent('Diseño');
    expect(item).toHaveAttribute('href', '/catalogo/cursos/7');
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });

  it('linksCareersToTheirDetailPage', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);

    await user.click(row('Administración y Negocios'));

    expect(screen.getByRole('link', { name: /Licenciatura en Administración/ })).toHaveAttribute('href', '/catalogo/carreras/2');
    expect(screen.getByRole('link', { name: /Marketing digital/ })).toHaveAttribute('href', '/catalogo/cursos/8');
  });

  it('pinsARowOnClickAndReleasesItOnTheSecondClick', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);

    await user.click(row('Diseño'));
    expect(row('Diseño')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Fijado')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Oferta de Diseño. Fijado');

    await user.click(row('Diseño'));
    expect(row('Diseño')).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByText('Fijado')).not.toBeInTheDocument();
  });

  it('ignoresHoverOverOtherRowsWhileARowIsPinned', async () => {
    respondWithSample();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);
    await user.click(row('Diseño'));

    await user.hover(row('De grado'));
    await user.hover(row('Administración y Negocios'));
    act(() => vi.advanceTimersByTime(400));

    expect(screen.getByRole('list', { name: 'Oferta de Diseño' })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Carreras de grado' })).not.toBeInTheDocument();
    expect(row('De grado')).toHaveAttribute('aria-pressed', 'false');
  });

  it('forgetsThePinnedRowWhenThePanelIsReopened', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);
    await user.click(row('Diseño'));
    await user.keyboard('{Escape}');

    await openPanel(user);

    expect(row('Diseño')).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByText('Elegí un área o un tipo para ver la oferta.')).toBeInTheDocument();
  });

  it('filtersTheListByNameWithTheSearchField', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);
    await user.click(row('Diseño'));

    await user.type(screen.getByRole('searchbox', { name: 'Buscar carrera o curso' }), 'foto');

    const list = screen.getByRole('list', { name: 'Oferta de Diseño' });
    expect(within(list).getAllByRole('link')).toHaveLength(1);
    expect(within(list).getByRole('link', { name: /Fotografía digital/ })).toBeInTheDocument();

    await user.clear(screen.getByRole('searchbox'));
    await user.type(screen.getByRole('searchbox'), 'zzz');
    expect(screen.getByText('No hay resultados para esa búsqueda.')).toBeInTheDocument();
  });

  it('keepsTypedSpacesAndHomeEndInsideTheSearchFieldInsteadOfMovingFocus', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);
    const search = screen.getByRole('searchbox');

    await user.type(search, 'ab{Home}x{End}y');

    expect(search).toHaveValue('xaby');
    expect(search).toHaveFocus();
  });

  it('movesWithTheKeyboardBetweenRowsZonesAndTheList', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    trigger().focus();

    await user.keyboard('{ArrowDown}');
    await screen.findByText('(3)');
    expect(row('Administración y Negocios')).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(row('Diseño')).toHaveFocus();
    await user.keyboard('{ArrowUp}{ArrowUp}');
    expect(row('Diseño')).toHaveFocus();
    await user.keyboard('{Home}');
    expect(row('Administración y Negocios')).toHaveFocus();

    await user.keyboard('{ArrowRight}');
    expect(row('Pregrado')).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(row('De grado')).toHaveFocus();
    expect(screen.getByRole('list', { name: 'Carreras de grado' })).toBeInTheDocument();

    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('link', { name: /Licenciatura en Diseño Gráfico/ })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('link', { name: /Licenciatura en Administración/ })).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(row('De grado')).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(row('Administración y Negocios')).toHaveFocus();
  });

  it('pinsWithEnterAndClosesWithEscapeReturningFocusToTheTrigger', async () => {
    respondWithSample();
    const user = userEvent.setup();
    renderBar();
    trigger().focus();
    await user.keyboard('{ArrowDown}');
    await screen.findByText('(3)');

    await user.keyboard('{Enter}');
    expect(row('Administración y Negocios')).toHaveAttribute('aria-pressed', 'true');

    await user.keyboard('{Escape}');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    expect(trigger()).toHaveFocus();
  });

  it('showsSkeletonsWhileLoading', async () => {
    server.use(
      http.get(`${API}/programs`, () => new Promise(() => {})),
      http.get(`${API}/courses`, () => new Promise(() => {})),
    );
    const user = userEvent.setup();
    renderBar();

    await user.click(trigger());

    expect(await screen.findByText('Cargando oferta')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Diseño' })).toBeInTheDocument();
  });

  it('showsARetryableErrorAndKeepsTheMenuUsable', async () => {
    server.use(
      http.get(`${API}/programs`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })),
      http.get(`${API}/courses`, () => HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 })),
    );
    const user = userEvent.setup();
    renderBar();
    await user.click(trigger());

    expect(await screen.findByText('No pudimos cargar la oferta académica')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver toda la oferta' })).toBeInTheDocument();

    respondWithSample();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('(3)')).toBeInTheDocument();
  });

  it('showsAShortEmptyStateWhenNothingIsPublished', async () => {
    respondWith([], []);
    const user = userEvent.setup();
    renderBar();

    await user.click(trigger());

    expect(await screen.findByText('Todavía no hay oferta publicada')).toBeInTheDocument();
  });

  it('hasNoAxeViolationsWithThePanelOpenAndARowPinned', async () => {
    respondWithSample();
    const user = userEvent.setup();
    const { container } = renderBar();
    await openPanel(user);
    await user.click(row('Diseño'));

    expect(await axe(container)).toHaveNoViolations();
  });
});
