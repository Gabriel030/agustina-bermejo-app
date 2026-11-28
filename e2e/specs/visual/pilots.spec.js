import { test, expect } from '@playwright/test';
import { stubContractApi } from './contractStub.js';
import {
  APPLICATION_CHECK,
  CAREER_ENROLLMENTS,
  CAREER_PROGRESS,
  CLOSURE_SHEET,
  CURRICULUM_PROGRESS,
  FIRST_DIRECTOR_STATUS,
  GRADE_ITEMS,
  GRADUATION_PROJECTION,
  PENDING_APPLICATIONS,
  ROSTER,
  SECTION_ENROLLMENTS,
  SETTINGS,
  TEACHING_SECTIONS,
} from './pilotsData.js';

const THEMES = ['light', 'dark'];
const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 800 },
  { name: 'mobile', width: 390, height: 844 },
];
const FIXED_TIME = '2026-11-25T15:00:00Z';

const STUDENT_OVERRIDES = {
  '/me/program-enrollments': CAREER_ENROLLMENTS,
  '/me/program-enrollments/41/progress': CAREER_PROGRESS,
  '/me/program-enrollments/41/progress/subjects': CURRICULUM_PROGRESS,
  '/me/program-enrollments/41/progress/projection': GRADUATION_PROJECTION,
  '/me/section-enrollments': SECTION_ENROLLMENTS,
  '/me/section-enrollments/301/grade-items': GRADE_ITEMS,
};

const SCREENS = [
  {
    name: 'mis-carreras',
    roles: ['ALUMNO'],
    path: '/alumno/mis-carreras?carrera=41&tab=materias',
    overrides: STUDENT_OVERRIDES,
    ready: (page) => expect(page.getByRole('heading', { name: 'Mis carreras' })).toBeVisible(),
    settled: (page) => expect(page.getByText('Enfermería Básica').first()).toBeVisible(),
  },
  {
    name: 'detalle-de-materia',
    roles: ['ALUMNO'],
    path: '/alumno/mis-carreras/materias/301',
    overrides: STUDENT_OVERRIDES,
    ready: (page) => expect(page.getByRole('heading', { name: 'Enfermería Básica' })).toBeVisible(),
    settled: (page) => expect(page.getByText('Parcial 1').first()).toBeVisible(),
  },
  {
    name: 'plan-de-estudio',
    roles: ['ALUMNO'],
    path: '/alumno/mis-carreras?carrera=41&tab=plan-de-estudio',
    overrides: STUDENT_OVERRIDES,
    ready: (page) => expect(page.getByRole('heading', { name: 'Mis carreras' })).toBeVisible(),
    settled: (page) => expect(page.getByText('Farmacología').first()).toBeVisible(),
    finish: (page) => page.getByText('Farmacología').first().scrollIntoViewIfNeeded(),
  },
  {
    name: 'solicitudes-de-ingreso',
    roles: ['ADMINISTRATIVO'],
    path: '/administrativo/solicitudes-de-ingreso',
    overrides: { '/program-enrollment-applications/pending': PENDING_APPLICATIONS },
    ready: (page) => expect(page.getByRole('heading', { name: /Solicitudes de ingreso/ })).toBeVisible(),
    settled: (page) => expect(page.getByText('Mateo Fernández').first()).toBeVisible(),
  },
  {
    name: 'solicitud-de-ingreso-detalle',
    roles: ['ADMINISTRATIVO'],
    path: '/administrativo/solicitudes-de-ingreso/51',
    overrides: {
      '/program-enrollment-applications/pending': PENDING_APPLICATIONS,
      '/program-enrollment-applications/51': PENDING_APPLICATIONS[0],
      '/program-enrollment-applications/51/approval-check': APPLICATION_CHECK,
    },
    ready: (page) => expect(page.getByText('Valentina Suárez').first()).toBeVisible(),
    settled: (page) => expect(page.getByRole('button', { name: /Aprobar/ }).first()).toBeVisible(),
  },
  {
    name: 'ajustes-del-instituto',
    roles: ['DIRECTOR'],
    path: '/director/ajustes-del-instituto',
    overrides: { '/institution/academic-settings': SETTINGS },
    ready: (page) => expect(page.getByRole('heading', { name: /Ajustes del instituto/ })).toBeVisible(),
    settled: (page) => expect(page.getByRole('main')).toBeVisible(),
  },
  {
    name: 'tomar-asistencia',
    roles: ['DOCENTE'],
    path: '/docente/tomar-asistencia?seccion=PROGRAM-5',
    overrides: {
      '/me/teaching-sections': TEACHING_SECTIONS,
      '/program-sections/5/roster': ROSTER,
      '/program-sections/5/attendance/history': { classDates: [], rows: [] },
      '/program-sections/5/class-sessions/2026-11-25': null,
    },
    ready: (page) => expect(page.getByRole('heading', { name: 'Tomar asistencia' })).toBeVisible(),
    settled: async (page) => {
      await expect(page.getByText('Ana Gómez')).toBeVisible();
      await page.getByRole('listitem').filter({ hasText: 'Ana Gómez' }).getByRole('button', { name: 'Presente' }).click();
      await page.getByRole('listitem').filter({ hasText: 'Bruno Díaz' }).getByRole('button', { name: 'Ausente' }).click();
      await expect(page.getByText('2 cambios sin guardar')).toBeVisible();
    },
  },
  {
    name: 'primer-director',
    roles: ['PLATFORM_ADMIN'],
    path: '/platform_admin/primer-director',
    overrides: { '/platform/provisioning/first-director/status': FIRST_DIRECTOR_STATUS },
    ready: (page) => expect(page.getByRole('main')).toBeVisible(),
    settled: (page) => expect(page.getByRole('main')).toBeVisible(),
  },
  {
    name: 'acta-de-cierre-tabla-ancha',
    viewports: ['desktop'],
    roles: ['DIRECTOR'],
    path: '/director/cierre-de-comision/7?type=PROGRAM',
    overrides: { '/program-sections/7/closure-sheet': CLOSURE_SHEET },
    ready: (page) => expect(page.getByRole('heading', { name: 'Cierre de comisión' })).toBeVisible(),
    settled: (page) => expect(page.getByText('Acosta').first()).toBeVisible(),
  },
  {
    name: 'acta-de-cierre-exportar',
    viewports: ['mobile'],
    roles: ['DIRECTOR'],
    path: '/director/cierre-de-comision/7?type=PROGRAM',
    overrides: { '/program-sections/7/closure-sheet': CLOSURE_SHEET },
    ready: (page) => expect(page.getByRole('heading', { name: 'Cierre de comisión' })).toBeVisible(),
    settled: async (page) => {
      await page.getByRole('button', { name: 'Exportar' }).click();
      await expect(page.getByRole('menuitem').first()).toBeVisible();
    },
  },
  {
    name: 'selector-de-mes',
    roles: ['DIRECTOR'],
    path: '/director/emision-mensual',
    overrides: { '/monthly-invoice-runs': [] },
    ready: (page) => expect(page.getByRole('button', { name: /^Mes, / })).toBeVisible(),
    settled: async (page) => {
      await page.getByRole('button', { name: /^Mes, / }).click();
      await page.getByRole('button', { name: /^marzo 2026/ }).click();
      await page.getByRole('button', { name: /^Mes, marzo 2026/ }).click();
      await expect(page.getByRole('button', { name: /^marzo 2026/ })).toHaveAttribute('aria-pressed', 'true');
    },
  },
];

async function openScreen(page, screen, theme) {
  await page.addInitScript(
    ([t]) => {
      localStorage.setItem('tema', t);
      localStorage.setItem('refreshToken', 'stub-refresh');
    },
    [theme],
  );
  await page.clock.setFixedTime(FIXED_TIME);
  await stubContractApi(page, { roles: screen.roles, overrides: screen.overrides });
  await page.goto(screen.path);
  await screen.ready(page);
  await screen.settled(page);
  await page.evaluate(() => {
    for (const element of [document.scrollingElement, ...document.querySelectorAll('*')]) {
      if (element && element.scrollTop > 0) element.scrollTop = 0;
    }
    return document.fonts.ready;
  });
  await screen.finish?.(page);
}

for (const theme of THEMES) {
  for (const viewport of VIEWPORTS) {
    const variant = `b-${theme}-${viewport.name}`;
    test.describe(`${variant} pilots`, () => {
      test.use({
        viewport: { width: viewport.width, height: viewport.height },
        timezoneId: 'America/Argentina/Buenos_Aires',
      });

      for (const screen of SCREENS.filter(({ viewports }) => !viewports || viewports.includes(viewport.name))) {
        test(`pilots ${screen.name}`, async ({ page }) => {
          await openScreen(page, screen, theme);
          await expect(page).toHaveScreenshot([variant, `${screen.name}.png`], { mask: [page.getByRole('navigation')] });
        });
      }
    });
  }
}
