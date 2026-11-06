import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TiraDeConsecuencias } from './TiraDeConsecuencias';

const base = { alcanzados: 3, total: 3 };

describe('TiraDeConsecuencias email legend', () => {
  it('urgent notice says it also goes by email even if the institute switch is off', () => {
    render(<TiraDeConsecuencias {...base} urgent emailEnabled={false} />);
    expect(screen.getByText('También por email.')).toBeInTheDocument();
  });

  it('non urgent notice without institute switch says campana only', () => {
    render(<TiraDeConsecuencias {...base} />);
    expect(screen.getByText(/Solo por campana/)).toBeInTheDocument();
    expect(screen.queryByText(/También por email/)).not.toBeInTheDocument();
  });

  it('non urgent notice with institute switch on says also by email', () => {
    render(<TiraDeConsecuencias {...base} emailEnabled />);
    expect(screen.getByText('También por email')).toBeInTheDocument();
  });

  it('shows no email legend when nobody is notified', () => {
    render(<TiraDeConsecuencias {...base} urgent sinNotificar />);
    expect(screen.queryByText(/email/)).not.toBeInTheDocument();
  });
});
