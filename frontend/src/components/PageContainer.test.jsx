import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PageContainer } from '@/components/PageContainer';

describe('PageContainer', () => {
  it('renders the page title as a heading and the header action', () => {
    render(
      <PageContainer title="Gestión académica" action={<button type="button">Nueva materia</button>}>
        <p>contenido</p>
      </PageContainer>,
    );

    expect(screen.getByRole('heading', { name: 'Gestión académica' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Nueva materia' })).toBeInTheDocument();
    expect(screen.getByText('contenido')).toBeInTheDocument();
  });

  it('renders no header when there is neither title nor action', () => {
    render(
      <PageContainer>
        <p>contenido</p>
      </PageContainer>,
    );

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });
});
