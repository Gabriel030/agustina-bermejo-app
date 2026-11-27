import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PublicLayout } from '@/layouts/PublicLayout';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: null }),
}));

vi.mock('@/pwa/SystemBar', () => ({ SystemBar: () => null }));

function renderLayout() {
  return renderWithProviders(
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/legal/terminos" element={<h1>Contenido</h1>} />
      </Route>
    </Routes>,
    { route: '/legal/terminos' },
  );
}

describe('PublicLayout', () => {
  it('rendersSkipLinkBeforeTheTopBar', () => {
    renderLayout();
    const skip = document.querySelector('a[href="#main-content"]');
    const brand = screen.getByRole('link', { name: 'Ir al inicio' });
    expect(skip.compareDocumentPosition(brand) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('rendersTheTopBarAboveTheMainContent', () => {
    renderLayout();
    const bar = screen.getByRole('banner');
    const main = screen.getByRole('main');
    expect(bar.compareDocumentPosition(main) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(main).toContainElement(screen.getByRole('heading', { name: 'Contenido' }));
    expect(bar).not.toContainElement(main);
  });
});
