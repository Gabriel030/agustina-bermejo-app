import { beforeEach, describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DatosPersonalesTab } from '@/features/identity/pages/tabs/DatosPersonalesTab';

const API = 'http://localhost:8080';

function record(extra = {}) {
  return {
    id: 44,
    nombre: 'Lucía',
    apellido: 'Fernández',
    legajo: '10066',
    dni: '40111666',
    email: 'lucia@demo.test',
    username: '40111666',
    status: 'ACTIVE',
    onboardingStatus: 'CONFIRMADO',
    domicilio: { street: null, number: null, floor: null, city: null, state: null, postalCode: null, country: null },
    motivoBaja: null,
    detalleBaja: null,
    ...extra,
  };
}

describe('DatosPersonalesTab', () => {
  beforeEach(() => {
    server.use(http.get(`${API}/students/44/documents`, () => HttpResponse.json([])));
  });

  it('does not show withdrawal information for an active student nor an empty address section', async () => {
    renderWithProviders(<DatosPersonalesTab legajo={record({ motivoBaja: 'CAMBIO_CARRERA' })} />);

    expect(await screen.findByText('Sin documentos cargados.')).toBeInTheDocument();
    expect(screen.queryByText('Información de baja')).not.toBeInTheDocument();
    expect(screen.queryByText('Domicilio')).not.toBeInTheDocument();
  });

  it('translates the withdrawal reason and the status of a withdrawn student', async () => {
    renderWithProviders(
      <DatosPersonalesTab legajo={record({ status: 'DELETED', motivoBaja: 'CAMBIO_CARRERA', detalleBaja: 'Se pasó a Kinesiología' })} />,
    );

    expect(await screen.findByText('Información de baja')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Cambio de carrera')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Dado de baja')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('CAMBIO_CARRERA')).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('Eliminado')).not.toBeInTheDocument();
  });

  it('shows the sex translated instead of its code', async () => {
    server.use(http.get(`${API}/students/53/documents`, () => HttpResponse.json([])));
    renderWithProviders(
      <DatosPersonalesTab
        legajo={{ id: 53, nombre: 'Ana', apellido: 'Pérez', sexo: 'F', status: 'ACTIVE' }}
        puedeAccionar={false}
        puedeDarDeBaja={false}
      />,
    );

    expect(await screen.findByDisplayValue('Femenino')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('F')).not.toBeInTheDocument();
  });
});
