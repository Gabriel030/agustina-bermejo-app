import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { firstRealPage, hasRealScreen, pageLabel, visibleMenuItems } from '@/routes/menuAccess';

let currentRole = 'DOCENTE';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: [currentRole] }, roles: [currentRole], logout: vi.fn() }),
}));

describe('menuAccess', () => {
  it('detects which pages of a role have a real screen', () => {
    expect(hasRealScreen('alumno', 'inicio')).toBe(true);
    expect(hasRealScreen('docente', 'inicio')).toBe(false);
    expect(hasRealScreen('alumno', 'opciones')).toBe(false);
    expect(hasRealScreen('administrativo', 'constancias')).toBe(false);
    expect(hasRealScreen('director', 'constancias')).toBe(false);
    expect(ROLE_CONFIG.director.pages).toContain('constancias');
    expect(ROLE_CONFIG.preceptor.pages).not.toContain('constancias');
  });

  it('picks the first page with a real screen as the landing page', () => {
    expect(firstRealPage('alumno', ROLE_CONFIG.alumno.pages)).toBe('inicio');
    expect(firstRealPage('docente', ROLE_CONFIG.docente.pages)).toBe('mis-comisiones');
    expect(firstRealPage('preceptor', ROLE_CONFIG.preceptor.pages)).toBe('alumnos');
    expect(firstRealPage('administrativo', ROLE_CONFIG.administrativo.pages)).toBe('alumnos');
    expect(firstRealPage('director', ROLE_CONFIG.director.pages)).toBe('alumnos');
    expect(firstRealPage('docente', ['inicio'])).toBeNull();
  });

  it('hides role items without a screen and keeps shared links', () => {
    const items = [
      { to: '/alumno/inicio', label: 'Inicio' },
      { to: '/alumno/opciones', label: 'Opciones' },
      { to: '/catalogo', label: 'Catálogo' },
    ];
    expect(visibleMenuItems('alumno', items).map((item) => item.label)).toEqual(['Inicio', 'Catálogo']);
  });

  it('labels pages readably instead of using the slug', () => {
    expect(pageLabel('gestion-academica')).toBe('Gestión académica');
    expect(pageLabel('algo-nuevo')).toBe('Algo nuevo');
  });

  it.each(Object.keys(ROLE_CONFIG))('shows no visible menu item of %s that lands on a placeholder', async (role) => {
    currentRole = role.toUpperCase();
    const { Layout } = ROLE_CONFIG[role];
    renderWithProviders(<ThemeProvider><Layout /></ThemeProvider>, { route: `/${role}/alumnos` });

    const hrefs = (await screen
      .findAllByRole('link', {}, { timeout: 10000 }))
      .map((link) => link.getAttribute('href'))
      .filter((href) => href?.startsWith(`/${role}/`));

    expect(hrefs.length).toBeGreaterThan(0);
    hrefs.forEach((href) => {
      expect(hasRealScreen(role, href.slice(`/${role}/`.length))).toBe(true);
    });
  });
});
