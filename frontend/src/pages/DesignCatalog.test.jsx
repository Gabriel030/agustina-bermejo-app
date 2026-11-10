import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { DesignCatalog } from '@/pages/DesignCatalog';

const SECTION_IDS = ['buttons', 'cards', 'pills', 'tabs', 'inputs', 'selects', 'table', 'dialog', 'overlays', 'skeleton', 'stepper', 'progress'];

function renderCatalog(route = '/dev/catalog') {
  return renderWithProviders(
    <ThemeProvider>
      <Routes>
        <Route path="/dev/catalog" element={<DesignCatalog />} />
      </Routes>
    </ThemeProvider>,
    { route },
  );
}

describe('DesignCatalog', () => {
  it('renders every catalog section', () => {
    renderCatalog();
    SECTION_IDS.forEach((id) => expect(screen.getByTestId(`catalog-${id}`)).toBeInTheDocument());
  });

  it('opens the dialog when the open parameter is dialog', () => {
    renderCatalog('/dev/catalog?open=dialog');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('keeps the dialog closed without the open parameter', () => {
    renderCatalog();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
