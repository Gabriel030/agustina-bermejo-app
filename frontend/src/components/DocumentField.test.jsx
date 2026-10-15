import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DocumentField } from '@/components/DocumentField';
import { validateDocument } from '@/lib/documentType';

function Harness({ initialType = 'DNI', error }) {
  const [type, setType] = useState(initialType);
  const [number, setNumber] = useState('');
  return <DocumentField type={type} number={number} onTypeChange={setType} onNumberChange={setNumber} error={error} />;
}

describe('DocumentField', () => {
  it('shows the DNI format hint by default', () => {
    render(<Harness />);
    expect(screen.getByText('El DNI tiene 7 u 8 números, sin puntos ni espacios')).toBeInTheDocument();
  });

  it('shows the passport hint and accepts letters when the type changes', async () => {
    const user = userEvent.setup();
    render(<Harness initialType="PASSPORT" />);
    expect(screen.getByText(/El pasaporte tiene entre 4 y 20 letras o números/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Número de documento'), 'AB123456');
    expect(screen.getByLabelText('Número de documento')).toHaveValue('AB123456');
  });

  it('replaces the hint with the error message', () => {
    render(<Harness error="Documento inválido" />);
    expect(screen.getByText('Documento inválido')).toBeInTheDocument();
    expect(screen.queryByText(/El DNI tiene 7 u 8/)).not.toBeInTheDocument();
  });
});

describe('validateDocument', () => {
  it('validates each type', () => {
    expect(validateDocument('DNI', '12345678')).toBeNull();
    expect(validateDocument('DNI', '1234567A')).not.toBeNull();
    expect(validateDocument('PASSPORT', 'AB123456')).toBeNull();
    expect(validateDocument('PASSPORT', 'AB-123')).not.toBeNull();
    expect(validateDocument('OTHER', 'abc')).not.toBeNull();
    expect(validateDocument('OTHER', '')).not.toBeNull();
  });
});
