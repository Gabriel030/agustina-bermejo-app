import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AniosLectivosSection } from './AniosLectivosSection';

const API = 'http://localhost:8080';

describe('AniosLectivosSection load error', () => {
  it('shows the retry state instead of telling the director to load the school year', async () => {
    server.use(http.get(`${API}/programs/4/year-billing-templates`, () => HttpResponse.json({ code: 'BOOM' }, { status: 500 })));
    renderWithProviders(<AniosLectivosSection programId={4} canEdit />);

    expect(await screen.findByText('No pudimos cargar los años lectivos')).toBeInTheDocument();
    expect(screen.queryByText('Sin años lectivos')).not.toBeInTheDocument();
  });
});
