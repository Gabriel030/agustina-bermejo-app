import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AvisoEditar } from './AvisoEditar';

const API = 'http://localhost:8080';
const SECTION_ID = 49;

describe('AvisoEditar', () => {
  it('un aviso enviado a un segmento muestra el total de la comisión, no la cantidad de destinatarios', async () => {
    server.use(
      http.get(`${API}/subject-sections/${SECTION_ID}/announcements`, () =>
        HttpResponse.json([
          {
            id: 10,
            title: 'Riesgo de quedar libre',
            body: 'Pasá por preceptoría.',
            category: 'REMINDER',
            urgent: true,
            expiresAt: null,
            audienceLabel: 'Con 17 o más inasistencias',
            status: 'SENT',
            sentAt: '2026-10-03T10:00:00',
            createdAt: '2026-10-03T10:00:00',
          },
        ]),
      ),
      http.get(`${API}/announcements/10/recipients`, () => HttpResponse.json([{ studentId: 1, file: 'A-1', name: 'Lucía Pérez' }])),
      http.post(`${API}/subject-sections/${SECTION_ID}/audience-preview`, () =>
        HttpResponse.json({ count: 4, totalInSection: 4, label: 'Toda la comisión', hasUngradedInstances: false, students: [] }),
      ),
    );

    renderWithProviders(
      <Routes>
        <Route path="/docente/mis-comisiones/:sectionId/avisos/:avisoId/editar" element={<AvisoEditar />} />
      </Routes>,
      { route: `/docente/mis-comisiones/${SECTION_ID}/avisos/10/editar` },
    );

    expect(await screen.findByText('Lo ve 1 de los 4 alumnos de la comisión.')).toBeInTheDocument();
  });
});
