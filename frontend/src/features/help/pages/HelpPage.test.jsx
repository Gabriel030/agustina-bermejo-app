import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { parseHelpTask } from '@/features/help/lib/helpTasks';
import { HelpPage } from '@/features/help/pages/HelpPage';

const authState = { roles: ['PRECEPTOR'] };

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 7, roles: authState.roles }, roles: authState.roles }),
}));

const task = (slug, section, title, roles, body) =>
  parseHelpTask(
    `../content/${section}/${slug}.md`,
    `---\ntitle: ${title}\nsection: ${section}\nroles: [${roles}]\nstatus: borrador\n---\n\n${body}`,
  );

const TASKS = [
  task('open-cohort', 'set-up-school-year', 'Abrir una cohorte', 'preceptor', '## Para qué sirve\n\nCrear la cohorte.\n\n## Pasos\n\n1. Entrá a Oferta académica.'),
  task('take-attendance', 'students', 'Tomar asistencia', 'preceptor, docente', '## Para qué sirve\n\nRegistrar presentes.'),
  task('approve-permissions', 'roles-and-permissions', 'Aprobar permisos', 'director', '## Para qué sirve\n\nDar acceso al personal.'),
  task('billing-pending', 'billing', 'Cobranza pendiente', 'preceptor', '## Para qué sirve\n\nEn rediseño.'),
];

vi.mock('@/features/help/lib/helpContentSource', () => ({
  INCLUDE_DRAFTS: true,
  loadHelpTasks: () => Promise.resolve(TASKS),
}));

const renderHelp = (role = 'PRECEPTOR', route = '/ayuda') => {
  authState.roles = [role];
  return renderWithProviders(<HelpPage />, { route });
};

beforeEach(() => {
  authState.roles = ['PRECEPTOR'];
  window.scrollTo = vi.fn();
});

describe('HelpPage', () => {
  it.each(['DOCENTE', 'PRECEPTOR', 'ADMINISTRATIVO', 'SUBDIRECTOR', 'DIRECTOR', 'PLATFORM_ADMIN'])(
    'opensTheHelpFor%s',
    async (role) => {
      renderHelp(role);

      expect(await screen.findByRole('searchbox', { name: 'Buscar en la ayuda' })).toBeInTheDocument();
    },
  );

  it('deniesAccessToStudents', () => {
    renderHelp('ALUMNO');

    expect(screen.getByText('La ayuda es para el personal del instituto')).toBeInTheDocument();
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument();
  });

  it('showsOnlySectionsWithTasksOfTheOwnRoleByDefault', async () => {
    renderHelp('PRECEPTOR');

    expect(await screen.findByRole('button', { name: /Armar el año/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Alumnos/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Permisos y roles/ })).not.toBeInTheDocument();
  });

  it('showsEverySectionWhenAllRolesSwitchIsOn', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR');
    await screen.findByRole('button', { name: /Armar el año/ });

    await user.click(screen.getByRole('switch', { name: 'Ver todos los roles' }));

    expect(screen.getByRole('button', { name: /Permisos y roles/ })).toBeInTheDocument();
  });

  it('marksBillingAsUnderRedesignWithoutOpeningIt', async () => {
    renderHelp('PRECEPTOR');

    expect(await screen.findByText('En rediseño')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cobranza/ })).not.toBeInTheDocument();
  });

  it('opensASectionAndThenATaskWithItsTableOfContents', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR');

    await user.click(await screen.findByRole('button', { name: /Armar el año/ }));
    await user.click(screen.getByRole('button', { name: /Abrir una cohorte/ }));

    expect(screen.getByRole('heading', { name: 'Abrir una cohorte', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'En esta tarea' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Pasos' }).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.getByRole('button', { name: /Abrir una cohorte/ })).toBeInTheDocument();
  });

  it('movesFocusToTheTaskTitleAndScrollsToTopWhenOpeningATask', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR');

    await user.click(await screen.findByRole('button', { name: /Armar el año/ }));
    await user.click(screen.getByRole('button', { name: /Abrir una cohorte/ }));

    expect(screen.getByRole('heading', { name: 'Abrir una cohorte', level: 2 })).toHaveFocus();
    expect(window.scrollTo).toHaveBeenCalledWith(0, 0);
  });

  it('closesTheOpenTaskAndShowsResultsWhenTypingInTheSearch', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR', '/ayuda?tarea=students/take-attendance');
    expect(await screen.findByRole('heading', { name: 'Tomar asistencia', level: 2 })).toBeInTheDocument();

    await user.type(screen.getByRole('searchbox'), 'asis');

    expect(screen.queryByRole('heading', { name: 'Tomar asistencia', level: 2 })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Tomar asistencia/ })).toBeInTheDocument();
  });

  it('turnsOnAllRolesWhenTheLinkedTaskBelongsToAnotherRole', async () => {
    renderHelp('PRECEPTOR', '/ayuda?tarea=roles-and-permissions/approve-permissions');

    expect(await screen.findByRole('heading', { name: 'Aprobar permisos', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Ver todos los roles' })).toBeChecked();
  });

  it('findsTasksByBodyTextIgnoringAccentsAndCase', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR');

    await user.type(await screen.findByRole('searchbox'), 'OFERTA ACADEMICA');

    expect(screen.getByRole('button', { name: /Abrir una cohorte/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Tomar asistencia/ })).not.toBeInTheDocument();
  });

  it('showsNoResultsAndSearchesWithinTheRoleFilter', async () => {
    const user = userEvent.setup();
    renderHelp('PRECEPTOR');

    await user.type(await screen.findByRole('searchbox'), 'acceso al personal');

    expect(screen.getByText('Sin resultados')).toBeInTheDocument();

    await user.click(screen.getByRole('switch', { name: 'Ver todos los roles' }));

    expect(screen.getByRole('button', { name: /Aprobar permisos/ })).toBeInTheDocument();
  });
});
