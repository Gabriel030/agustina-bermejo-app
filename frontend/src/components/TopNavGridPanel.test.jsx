import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { ThemeProvider } from '@/context/ThemeContext';
import { TopNav } from '@/components/TopNav';
import { groupMenuItems, menuItemsFor } from '@/routes/roleMenu';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 7, nombre: 'Ana', apellido: 'Pérez', roles: ['ADMINISTRATIVO'] }, roles: ['ADMINISTRATIVO'], logout: vi.fn() }),
}));

afterEach(() => vi.useRealTimers());

function renderBar({ route = '/administrativo/inicio' } = {}) {
  const items = menuItemsFor('administrativo');
  return renderWithProviders(
    <ThemeProvider>
      <TopNav titulo="Administrativo" items={items} groups={groupMenuItems('administrativo', items)} />
    </ThemeProvider>,
    { route },
  );
}

const trigger = () => screen.getByRole('button', { name: 'Académico' });
const row = (name) => screen.getByRole('button', { name: new RegExp(name) });

async function openPanel(user) {
  await user.click(trigger());
}

describe('TopNavGridPanel', () => {
  it('showsTheSectionsWithTheirCountAndAPlaceholderUntilOneIsPointed', async () => {
    const user = userEvent.setup();
    renderBar();

    await openPanel(user);

    expect(row('Oferta y períodos')).toHaveTextContent('(3)');
    expect(row('Cursada')).toHaveTextContent('(3)');
    expect(row('Egresos y certificados')).toHaveTextContent('(6)');
    expect(screen.getByText('Elegí un grupo para ver sus pantallas.')).toBeInTheDocument();
  });

  it('switchesTheListAfter140msOfHoverOverARow', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);

    await user.hover(row('Cursada'));
    act(() => vi.advanceTimersByTime(100));
    expect(screen.queryByRole('link', { name: 'Mesas de examen' })).not.toBeInTheDocument();
    act(() => vi.advanceTimersByTime(100));

    expect(screen.getByRole('link', { name: 'Mesas de examen' })).toHaveAttribute('href', '/administrativo/mesas-de-examen');
    expect(screen.queryByRole('link', { name: 'Feriados' })).not.toBeInTheDocument();
  });

  it('pinsASectionOnClickAndReleasesItOnTheSecondClick', async () => {
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);

    await user.click(row('Cursada'));
    expect(row('Cursada')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('Fijado')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Pantallas de Cursada. Fijado');

    await user.click(row('Cursada'));
    expect(row('Cursada')).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByText('Fijado')).not.toBeInTheDocument();
  });

  it('ignoresHoverOverOtherRowsWhileASectionIsPinned', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderBar();
    await openPanel(user);
    await user.click(row('Cursada'));

    await user.hover(row('Oferta y períodos'));
    act(() => vi.advanceTimersByTime(400));

    expect(screen.getByRole('link', { name: 'Mesas de examen' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Feriados' })).not.toBeInTheDocument();
  });

  it('movesBetweenRowsAndIntoTheListWithTheKeyboard', async () => {
    const user = userEvent.setup();
    renderBar();
    trigger().focus();

    await user.keyboard('{ArrowDown}');
    expect(row('Oferta y períodos')).toHaveFocus();
    expect(screen.getByRole('link', { name: 'Feriados' })).toBeInTheDocument();

    await user.keyboard('{ArrowDown}');
    expect(row('Cursada')).toHaveFocus();
    await user.keyboard('{End}');
    expect(row('Egresos y certificados')).toHaveFocus();
    await user.keyboard('{Home}');
    expect(row('Oferta y períodos')).toHaveFocus();

    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('link', { name: 'Gestión académica' })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('link', { name: 'Períodos' })).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(row('Oferta y períodos')).toHaveFocus();
  });

  it('returnsFromTheListToThePinnedRow', async () => {
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);
    await user.click(row('Cursada'));

    screen.getByRole('link', { name: 'Mesas de examen' }).focus();
    await user.keyboard('{ArrowLeft}');

    expect(row('Cursada')).toHaveFocus();
  });

  it('closesWithEscapeAndReturnsFocusToTheTrigger', async () => {
    const user = userEvent.setup();
    renderBar();
    trigger().focus();
    await user.keyboard('{ArrowDown}');

    await user.keyboard('{Escape}');

    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    expect(trigger()).toHaveFocus();
  });

  it('forgetsThePinnedSectionWhenThePanelIsReopened', async () => {
    const user = userEvent.setup();
    renderBar();
    await openPanel(user);
    await user.click(row('Cursada'));
    await user.keyboard('{Escape}');

    await openPanel(user);

    expect(row('Cursada')).toHaveAttribute('aria-pressed', 'false');
  });

  it('marksTheCurrentScreenInsideItsSectionAndTheTrigger', async () => {
    const user = userEvent.setup();
    renderBar({ route: '/administrativo/feriados' });

    expect(trigger()).toHaveClass('border-b-(--accent)');
    await openPanel(user);
    await user.click(row('Oferta y períodos'));

    const holidays = within(screen.getByRole('list', { name: 'Oferta y períodos' })).getByRole('link', { name: 'Feriados' });
    expect(holidays).toHaveAttribute('aria-current', 'page');
    expect(holidays).toHaveClass('bg-accent-soft');
  });

  it('hasNoAxeViolationsWithThePanelOpenAndASectionPinned', async () => {
    const user = userEvent.setup();
    const { container } = renderBar();
    await openPanel(user);
    await user.click(row('Cursada'));

    expect(await axe(container)).toHaveNoViolations();
  });
});
