import { describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { useBreadcrumb } from '@/hooks/useBreadcrumb';

function labelsFor(url) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  const { result } = renderHook(() => useBreadcrumb(), { wrapper });
  return result.current.map((item) => item.label);
}

describe('useBreadcrumb en Mis carreras', () => {
  it.each([
    ['tramites', 'Trámites'],
    ['egreso', 'Trámites'],
    ['plan-de-estudio', 'Plan de estudio'],
    ['mesas-de-examen', 'Mesas de examen'],
    ['notas', 'Notas'],
    ['asistencia', 'Asistencia'],
  ])('la pestaña %s se muestra como %s', (tab, label) => {
    expect(labelsFor(`/alumno/mis-carreras?tab=${tab}`)).toEqual(['Mis carreras', label]);
  });

  it('la pestaña por defecto no agrega un tramo', () => {
    expect(labelsFor('/alumno/mis-carreras')).toEqual(['Mis carreras']);
  });
});
