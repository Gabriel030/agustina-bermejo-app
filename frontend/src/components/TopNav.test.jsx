import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { axe } from '@/test/axe';
import { ThemeProvider } from '@/context/ThemeContext';
import { TopNav } from '@/components/TopNav';
import { groupMenuItems, menuItemsFor } from '@/routes/roleMenu';

const logout = vi.fn();
const authState = { roles: ['DIRECTOR'] };

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    usuario: { id: 7, nombre: 'Ana', apellido: 'Pérez', roles: authState.roles },
    roles: authState.roles,
    logout,
  }),
}));

afterEach(() => {
  logout.mockClear();
  authState.roles = ['DIRECTOR'];
});

function renderTopNav({ role = 'director', route = '/director/inicio', filter = () => true } = {}) {
  const items = menuItemsFor(role).filter(filter);
  return renderWithProviders(
    <ThemeProvider>
      <TopNav titulo="Director" items={items} groups={groupMenuItems(role, items)} />
    </ThemeProvider>,
    { route },
  );
}

const trigger = (name) => screen.getByRole('button', { name });

describe('TopNav', () => {
  it('exposesGroupTriggersAsCollapsedPopupButtonsLinkedToTheirPanel', () => {
    renderTopNav();

    const academic = trigger('Académico');
    expect(academic).toHaveAttribute('aria-expanded', 'false');
    expect(academic).toHaveAttribute('aria-haspopup', 'true');
    expect(document.getElementById(academic.getAttribute('aria-controls'))).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Gestión académica' })).not.toBeInTheDocument();
  });

  it('keepsSingleItemsAsPlainLinksOutsideAnyMenu', () => {
    renderTopNav();

    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/director/inicio');
    expect(screen.getByRole('link', { name: 'Aprobaciones' })).toHaveAttribute('href', '/director/aprobaciones');
    expect(screen.queryByRole('button', { name: 'Inicio' })).not.toBeInTheDocument();
  });

  it('opensAndClosesAGroupOnClick', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger('Personas'));
    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('link', { name: 'Alumnos' })).toHaveAttribute('href', '/director/alumnos');

    await user.click(trigger('Personas'));
    await waitFor(() => expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false'));
  });

  it('opensOnHoverAndClosesAfterTheGracePeriod', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.hover(trigger('Personas'));
    await waitFor(() => expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true'));

    await user.unhover(trigger('Personas'));
    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
    await waitFor(() => expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false'), { timeout: 1500 });
  });

  it('staysOpenWhenTheMouseComesBackWithinTheGracePeriod', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.hover(trigger('Personas'));
    await waitFor(() => expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true'));
    await user.unhover(trigger('Personas'));
    await user.hover(trigger('Personas'));
    await new Promise((resolve) => setTimeout(resolve, 350));

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
  });

  it('keepsTheMenuOpenWhenClickingAfterOpeningItWithHover', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.hover(trigger('Personas'));
    await waitFor(() => expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true'));
    await user.click(trigger('Personas'));

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
  });

  it.each([
    ['Enter', '{Enter}'],
    ['Space', ' '],
    ['ArrowDown', '{ArrowDown}'],
  ])('opensWithTheKeyboardUsing%sAndFocusesTheFirstItem', async (_name, keys) => {
    const user = userEvent.setup();
    renderTopNav();

    trigger('Personas').focus();
    await user.keyboard(keys);

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
    await waitFor(() => expect(screen.getByRole('link', { name: 'Alumnos' })).toHaveFocus());
  });

  it('movesFocusBetweenMenuItemsWithArrowKeys', async () => {
    const user = userEvent.setup();
    renderTopNav();

    trigger('Personas').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('link', { name: 'Alumnos' })).toHaveFocus());
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('link', { name: 'Personal' })).toHaveFocus();
    await user.keyboard('{ArrowUp}{ArrowUp}');
    expect(screen.getByRole('link', { name: 'Invitaciones' })).toHaveFocus();
  });

  it('closesWithEscapeAndReturnsFocusToTheTrigger', async () => {
    const user = userEvent.setup();
    renderTopNav();

    trigger('Personas').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('link', { name: 'Alumnos' })).toHaveFocus());
    await user.keyboard('{Escape}');

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
    expect(trigger('Personas')).toHaveFocus();
  });

  it('opensOnlyOneMenuAtATime', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger('Personas'));
    await user.click(trigger('Académico'));

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
    expect(trigger('Académico')).toHaveAttribute('aria-expanded', 'true');
  });

  it('marksTheGroupHoldingTheCurrentRouteAndItsItemInsideTheMenu', async () => {
    const user = userEvent.setup();
    renderTopNav({ route: '/director/gestion-academica' });

    expect(trigger('Académico')).toHaveClass('border-b-(--accent)');
    expect(trigger('Personas')).not.toHaveClass('border-b-(--accent)');

    await user.click(trigger('Académico'));
    expect(screen.getByRole('link', { name: 'Gestión académica' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Gestión académica' })).toHaveClass('bg-accent-soft');
    expect(screen.getByRole('link', { name: 'Oferta académica' })).not.toHaveAttribute('aria-current');
  });

  it('marksASingleLinkAsCurrentWithoutOpeningAnything', () => {
    renderTopNav({ route: '/director/aprobaciones' });

    expect(screen.getByRole('link', { name: 'Aprobaciones' })).toHaveAttribute('aria-current', 'page');
    expect(trigger('Académico')).not.toHaveClass('border-b-(--accent)');
  });

  it('lays the long menus out in two columns and the short ones in one', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger('Facturación'));
    expect(screen.getByRole('list', { name: 'Facturación' })).toHaveClass('grid-cols-2');
    await user.click(trigger('Personas'));
    expect(screen.getByRole('list', { name: 'Personas' })).not.toHaveClass('grid-cols-2');
  });

  it('closesTheMenuWhenAnItemIsChosen', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger('Personas'));
    await user.click(screen.getByRole('link', { name: 'Personal' }));

    expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
  });

  it('doesNotRenderItemsTheMenuAccessFilterHid', async () => {
    const user = userEvent.setup();
    renderTopNav({ filter: (item) => !item.to.endsWith('/facturacion') && !item.to.endsWith('/descuentos') });

    await user.click(trigger('Facturación'));
    expect(screen.queryByRole('link', { name: 'Descuentos' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Facturación' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Precios' })).toBeInTheDocument();
  });

  it('dropsAGroupWhoseItemsWereAllHidden', () => {
    renderTopNav({ filter: (item) => !item.to.endsWith('/inscripciones/listas-de-espera') });

    expect(screen.queryByRole('button', { name: 'Inscripciones' })).not.toBeInTheDocument();
  });

  it('showsTheStudentStudiesGroupWithPaymentsAndCatalogAsPlainLinks', async () => {
    const user = userEvent.setup();
    authState.roles = ['ALUMNO'];
    renderTopNav({ role: 'alumno', route: '/alumno/inicio' });

    const bar = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(bar).getAllByRole('link').map((link) => link.textContent)).toEqual(['Inicio', 'Pagos', 'Catálogo']);
    expect(within(bar).getByRole('link', { name: 'Catálogo' })).toHaveAttribute('href', '/catalogo');
    await user.click(trigger('Estudios'));
    expect(within(screen.getByRole('list', { name: 'Estudios' })).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Calendario',
      'Mis cursos',
      'Mis carreras',
      'Mis certificados',
    ]);
  });

  it('hasNoOptionsEntryInTheBarNorInTheUserMenuForTheStudent', async () => {
    const user = userEvent.setup();
    authState.roles = ['ALUMNO'];
    renderTopNav({ role: 'alumno', route: '/alumno/inicio' });

    const bar = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(bar).queryByRole('link', { name: 'Opciones' })).not.toBeInTheDocument();
    await user.click(trigger(/Mi cuenta/));
    expect(screen.queryByRole('link', { name: 'Opciones' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mi perfil' })).toHaveAttribute('href', '/perfil');
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('showsTheUserNameRoleAndLogoutInTheUserMenu', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger(/Mi cuenta/));
    expect(screen.getAllByText('Ana Pérez').length).toBeGreaterThan(0);
    expect(screen.getByText('Director')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Opciones' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('offersTheRoleSwitcherOnlyWhenTheUserHasSeveralRoles', async () => {
    const user = userEvent.setup();
    authState.roles = ['DIRECTOR', 'ALUMNO'];
    renderTopNav();

    await user.click(trigger(/Mi cuenta/));
    const roles = screen.getByRole('list', { name: 'Cambiar de rol' });
    expect(within(roles).getByRole('link', { name: /Alumno/ })).toHaveAttribute('href', '/alumno/inicio');
    expect(within(roles).getByRole('link', { name: /Director/ })).toHaveAttribute('aria-current', 'true');
    expect(screen.queryByRole('link', { name: 'Opciones' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mi perfil' })).toHaveAttribute('href', '/perfil');
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
  });

  it('hasNoRoleSwitcherForASingleRole', async () => {
    const user = userEvent.setup();
    renderTopNav();

    await user.click(trigger(/Mi cuenta/));

    expect(screen.queryByRole('list', { name: 'Cambiar de rol' })).not.toBeInTheDocument();
  });

  it('hasNoAxeViolationsWithAMenuOpen', async () => {
    const user = userEvent.setup();
    const { container } = renderTopNav({ route: '/director/gestion-academica' });

    await user.click(trigger('Académico'));

    expect(await axe(container)).toHaveNoViolations();
  });

  describe('hover timing', () => {
    afterEach(() => vi.useRealTimers());

    it('opensAfter90msOfHoverAndClosesAfter220msOfGrace', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderTopNav();

      await user.hover(trigger('Personas'));
      act(() => vi.advanceTimersByTime(60));
      expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
      act(() => vi.advanceTimersByTime(60));
      expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');

      await user.unhover(trigger('Personas'));
      act(() => vi.advanceTimersByTime(180));
      expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'true');
      act(() => vi.advanceTimersByTime(80));
      expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
    });

    it('doesNotOpenWhenThePointerLeavesBeforeTheOpenDelay', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderTopNav();

      await user.hover(trigger('Personas'));
      act(() => vi.advanceTimersByTime(20));
      await user.unhover(trigger('Personas'));
      act(() => vi.advanceTimersByTime(500));

      expect(trigger('Personas')).toHaveAttribute('aria-expanded', 'false');
    });
  });
});
