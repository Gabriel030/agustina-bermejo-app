import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCarrera } from '@/pages/DetalleCarrera';
import { InscripcionCarrera } from '@/pages/InscripcionCarrera';

const API = 'http://localhost:8080';

function renderDetalle(id = '1') {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
    </Routes>,
    { route: `/catalogo/carreras/${id}` },
  );
}

// El wizard de inscripción usa useAuth (Paso 1 precarga dni/dirección desde
// `usuario`) — a diferencia de DetalleCarrera, necesita AuthProvider real.
function renderConWizard(id = '1') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/catalogo/carreras/${id}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
            <Route path="/catalogo/carreras/:id/inscribirme" element={<InscripcionCarrera />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DetalleCarrera', () => {
  it('muestra nombre, precios de las 2 cohortes y el plan de estudio', async () => {
    server.use(
      http.get(`${API}/programs/1/subjects`, () =>
        HttpResponse.json([{ id: 1, subjectName: 'Álgebra', curricularYear: 1, credits: 8 }]),
      ),
    );
    renderDetalle();

    expect(await screen.findByText('Carrera de prueba')).toBeInTheDocument();
    expect(screen.getByText('$ 50.000')).toBeInTheDocument();
    expect(screen.getByText('$ 25.000')).toBeInTheDocument();
    expect(screen.getByText('Álgebra')).toBeInTheDocument();
  });

  it('el botón Inscribirme aparece bloqueado cuando no hay cohortes abiertas', async () => {
    server.use(http.get(`${API}/programs/1/cohorts`, () => HttpResponse.json([])));
    renderDetalle();

    const boton = await screen.findByRole('button', { name: 'Inscribirme' });
    expect(boton).toBeDisabled();
    expect(await screen.findByText('Todavía no hay cohortes abiertas para esta carrera.')).toBeInTheDocument();
  });

  it('Inscribirme navega a una página propia, no abre un modal', async () => {
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByText('Cohorte')).toBeInTheDocument();
  });

  it('el flujo completo pide cohorte, documentación, comprobante y confirma', async () => {
    server.use(
      http.post(`${API}/me/program-enrollment-applications`, () =>
        HttpResponse.json({ id: 5, status: 'PENDING' }, { status: 201 }),
      ),
    );
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));

    // Paso cohorte
    await user.click(await screen.findByText('Marzo 2026'));

    // Paso documentación: email + dni + dirección + 3 documentos
    await user.type(screen.getByPlaceholderText('tu@email.com'), 'alumno@example.com');
    await user.type(screen.getByPlaceholderText('DNI sin puntos'), '30111222');
    // Nombre/Apellido (disabled) + Email + DNI son los primeros 4 textbox; Calle/
    // Número/Piso/Ciudad/Provincia/CP le siguen en ese orden, sin placeholder propio.
    const [, , , , calleInput, numeroInput, , ciudadInput] = screen.getAllByRole('textbox');
    await user.type(calleInput, 'Calle Falsa');
    await user.type(numeroInput, '123');
    await user.type(ciudadInput, 'Ciudad');

    const fileInputs = document.querySelectorAll('input[type="file"]');
    const dniFrente = new File(['contenido'], 'dni-frente.jpg', { type: 'image/jpeg' });
    const dniDorso = new File(['contenido'], 'dni-dorso.jpg', { type: 'image/jpeg' });
    const analitico = new File(['contenido'], 'analitico.pdf', { type: 'application/pdf' });
    await user.upload(fileInputs[0], dniFrente);
    await user.upload(fileInputs[1], dniDorso);
    await user.upload(fileInputs[2], analitico);
    expect(screen.getAllByText('Cargado correctamente')).toHaveLength(3);

    await user.click(screen.getByRole('button', { name: 'Continuar →' }));

    // Paso pago
    expect(await screen.findByText('$ 50.000')).toBeInTheDocument();
    const confirmarAntesDeSubir = screen.getByRole('button', { name: 'Confirmar solicitud' });
    expect(confirmarAntesDeSubir).toBeDisabled();

    const comprobante = new File(['contenido'], 'comprobante.pdf', { type: 'application/pdf' });
    const [comprobanteInput] = document.querySelectorAll('input[type="file"]');
    await user.upload(comprobanteInput, comprobante);

    const confirmar = screen.getByRole('button', { name: 'Confirmar solicitud' });
    expect(confirmar).toBeEnabled();
    await user.click(confirmar);

    // El click abre un modal de confirmación — todavía no se envió nada.
    const modal = await screen.findByRole('dialog');
    await user.click(within(modal).getByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada')).toBeInTheDocument();
  });

  it('permite volver a un paso anterior sin perder lo ya cargado', async () => {
    const user = userEvent.setup();
    renderConWizard();

    await user.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    await user.click(await screen.findByText('Marzo 2026'));

    await user.type(screen.getByPlaceholderText('tu@email.com'), 'alumno@example.com');
    await user.type(screen.getByPlaceholderText('DNI sin puntos'), '30111222');

    // Volver a Cohorte (botón y click en el paso del stepper) y adelante de nuevo.
    await user.click(screen.getByRole('button', { name: '← Volver' }));
    expect(await screen.findByText('Marzo 2026')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Documentación' }));

    // El email y el dni siguen cargados — no se perdieron al navegar.
    expect(screen.getByPlaceholderText('tu@email.com')).toHaveValue('alumno@example.com');
    expect(screen.getByPlaceholderText('DNI sin puntos')).toHaveValue('30111222');
  });
});
