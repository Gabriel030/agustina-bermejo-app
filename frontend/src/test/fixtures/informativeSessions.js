export function informativeSession(overrides = {}) {
  return {
    id: 1,
    title: 'Conocé la Licenciatura en Administración',
    scheduledAt: '2026-12-18T22:00:00Z',
    modality: 'ONLINE',
    meetingUrl: 'https://meet.example.org/admin',
    location: null,
    capacity: 20,
    registrationCount: 6,
    relatedProgramId: 7,
    status: 'SCHEDULED',
    registeredByMe: false,
    ...overrides,
  };
}
