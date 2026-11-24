import { afterEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { restoreViewport, useCompactViewport } from '@/test/compactViewport';
import { EQUIVALENCE_ENROLLMENT_IDS } from '@/test/mocks/subjectEquivalenceHandlers';
import { EquivalencesSection } from './EquivalencesSection';

const API = 'http://localhost:8080';
const { empty, active, mixed } = EQUIVALENCE_ENROLLMENT_IDS;

afterEach(() => {
  restoreViewport();
});

function renderSection(programEnrollmentId, props = {}) {
  return renderWithProviders(<EquivalencesSection programEnrollmentId={programEnrollmentId} {...props} />);
}

describe('EquivalencesSection', () => {
  it('lists the active equivalences by default with their reason visible', async () => {
    renderSection(mixed);

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
    expect(screen.getByText('Lengua y Comunicación')).toBeInTheDocument();
    expect(screen.queryByText('Física I')).not.toBeInTheDocument();
    expect(screen.getAllByText('Motivo: Equivalencia por contenidos y carga horaria compatibles.')).not.toHaveLength(0);
    expect(screen.getAllByText('Vigente')).toHaveLength(3);
  });

  it('shows who voided each voided equivalence, when and why', async () => {
    const user = userEvent.setup();
    renderSection(mixed);

    await user.click(await screen.findByRole('radio', { name: 'Anuladas' }));

    expect(await screen.findByText('Física I')).toBeInTheDocument();
    expect(screen.queryByText('Matemática I')).not.toBeInTheDocument();
    expect(screen.getByText('Anulada por Raúl Díaz el 02/10/2026 14:00')).toBeInTheDocument();
    expect(screen.getByText('Motivo: La resolución fue cargada contra la materia equivocada.')).toBeInTheDocument();
  });

  it('lists every equivalence under Todas', async () => {
    const user = userEvent.setup();
    renderSection(mixed);

    await user.click(await screen.findByRole('radio', { name: 'Todas' }));

    expect(await screen.findByText('Física I')).toBeInTheDocument();
    expect(screen.getByText('Matemática I')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Ver detalle de/ })).toHaveLength(4);
  });

  it('says there are no equivalences when the student has none', async () => {
    renderSection(empty);

    expect(await screen.findByText('Sin equivalencias')).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: 'Anuladas' })).not.toBeInTheDocument();
  });

  it('explains an empty filter and offers to see all', async () => {
    const user = userEvent.setup();
    renderSection(active);

    await user.click(await screen.findByRole('radio', { name: 'Anuladas' }));

    expect(await screen.findByText('Ninguna equivalencia coincide')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ver todas' }));
    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
  });

  it('marks equivalences recognized for a subject outside the current plan', async () => {
    const user = userEvent.setup();
    renderSection(mixed);

    await user.click(await screen.findByRole('radio', { name: 'Todas' }));

    expect(await screen.findByText('Fuera del plan vigente')).toBeInTheDocument();
    expect(screen.getAllByText('Introducción a la Programación').length).toBeGreaterThan(0);
  });

  it('shows no-access inside the section when reading equivalences is forbidden', async () => {
    server.use(http.get(`${API}/program-enrollments/:id/equivalences`, () => new HttpResponse(null, { status: 403 })));
    renderSection(active);

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });

  it('offers Reintentar when the equivalences fail to load', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/program-enrollments/:id/equivalences`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
      }),
    );
    const user = userEvent.setup();
    renderSection(active);

    await user.click(await screen.findByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Sin equivalencias')).toBeInTheDocument();
  });

  it('shows the read-only notice only when asked and never offers load or void actions', async () => {
    const { unmount } = renderSection(mixed, { readOnlyNotice: true });

    expect(await screen.findByText('Solo lectura')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cargar|Anular/ })).not.toBeInTheDocument();
    unmount();

    renderSection(mixed);
    await screen.findByText('Matemática I');
    expect(screen.queryByText('Solo lectura')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cargar|Anular/ })).not.toBeInTheDocument();
  });

  it('uses column headers with scope col', async () => {
    renderSection(active);

    await screen.findByText('Matemática I');
    const headers = screen.getAllByRole('columnheader');
    expect(headers.length).toBeGreaterThanOrEqual(5);
    headers.forEach((header) => expect(header).toHaveAttribute('scope', 'col'));
  });

  it('opens the detail of a voided equivalence by id with reason, who and when', async () => {
    const user = userEvent.setup();
    renderSection(mixed);

    await user.click(await screen.findByRole('radio', { name: 'Anuladas' }));
    const trigger = await screen.findByRole('button', { name: 'Ver detalle de Física I' });
    await user.click(trigger);

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Física I' })).toBeInTheDocument();
    expect(within(dialog).getByText('Anulada por')).toBeInTheDocument();
    expect(within(dialog).getByText('Raúl Díaz')).toBeInTheDocument();
    expect(within(dialog).getByText('02/10/2026 14:00')).toBeInTheDocument();
    expect(within(dialog).getByText('La resolución fue cargada contra la materia equivocada.')).toBeInTheDocument();
    expect(within(dialog).getByText('Resolución')).toBeInTheDocument();
    expect(within(dialog).getByText('RES-2026-090')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('keeps the reason of an active equivalence in its detail', async () => {
    const user = userEvent.setup();
    renderSection(active);

    await user.click(await screen.findByRole('button', { name: 'Ver detalle de Matemática I' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Equivalencia por contenidos y carga horaria compatibles.')).toBeInTheDocument();
    expect(within(dialog).queryByText('Anulación')).not.toBeInTheDocument();
  });

  it('describes the origin in a tooltip reachable by keyboard that closes with Escape', async () => {
    const user = userEvent.setup();
    renderSection(active);

    const trigger = await screen.findByRole('button', { name: 'Origen de Matemática I' });
    await user.tab();
    trigger.focus();

    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Universidad Nacional de Cuyo');
    expect(trigger).toHaveAttribute('aria-describedby');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
  });

  it('keeps the tooltip open while the pointer moves onto it', async () => {
    const user = userEvent.setup();
    renderSection(active);

    await user.hover(await screen.findByRole('button', { name: 'Origen de Matemática I' }));
    const tooltip = await screen.findByRole('tooltip');
    const content = document.querySelector('[data-slot="tooltip-content"]');
    await user.hover(content);

    expect(tooltip).toBeInTheDocument();
    expect(screen.getAllByText(/Universidad Nacional de Cuyo/).length).toBeGreaterThan(0);
  });

  it('renders cards without tooltips on touch widths and opens the detail instead', async () => {
    useCompactViewport();
    const user = userEvent.setup();
    renderSection(active);

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Origen de/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Ver detalle de Matemática I' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Universidad Nacional de Cuyo')).toBeInTheDocument();
  });
});
