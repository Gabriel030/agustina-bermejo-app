import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { installFixedClock } from '@/test/fixedClock'
import { offlineQuery } from '@/api/offlineQuery'
import { toScheduleView } from '@/features/schedule/hooks/useMySchedule'
import { ScheduleCard } from './ScheduleCard'

installFixedClock({ advanceWithRealTime: true })

const API = 'http://localhost:8080'

const RESPONSE = {
  generatedAt: '2026-11-05T12:00:00',
  items: [
    { sectionEnrollmentId: 1, courseName: 'Curso A', sectionName: 'Comisión 1', courseSubjectName: 'Anatomía', schedule: 'Viernes 08:30', instructorTitular: 'Ana Pérez' },
    { sectionEnrollmentId: 2, courseName: 'Curso A', sectionName: 'Comisión 2', courseSubjectName: 'Fisiología', schedule: 'A coordinar', instructorTitular: 'Luis Gómez' },
    { sectionEnrollmentId: 3, courseName: 'Curso A', sectionName: 'Comisión 3', courseSubjectName: 'Histología', schedule: 'Jueves 19:00', instructorTitular: 'Luis Gómez' },
  ],
}

describe('ScheduleCard', () => {
  it('showsNextClassTodayBadgeAndUnparsableTextAsIs', async () => {
    server.use(http.get(`${API}/me/schedule`, () => HttpResponse.json(RESPONSE)))
    renderWithProviders(<ScheduleCard />)
    expect(await screen.findByText('Próxima clase')).toBeInTheDocument()
    expect(screen.getByText('Hoy 19:00')).toBeInTheDocument()
    expect(screen.getByText('A coordinar')).toBeInTheDocument()
    expect(screen.getAllByText('Hoy')).toHaveLength(1)
  })

  it('offlineHookMarksLevelAndIsWhitelisted', () => {
    const options = offlineQuery('N1', { queryKey: ['x'], queryFn: () => null })
    expect(options.meta).toEqual({ offline: true, offlineLevel: 'N1' })
    expect(() => offlineQuery('N9', {})).toThrow()
  })

  it('scheduleQueryNeverIncludesFinalScore', () => {
    const tainted = { ...RESPONSE, items: [{ ...RESPONSE.items[0], finalScore: 9, finalGradeType: 'X' }] }
    expect(JSON.stringify(toScheduleView(tainted))).not.toMatch(/finalScore|finalGradeType/)
  })
})
