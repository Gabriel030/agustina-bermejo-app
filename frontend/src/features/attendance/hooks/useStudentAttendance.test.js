import { describe, expect, it } from 'vitest'
import { toAttendanceSummary } from './useStudentAttendance'

describe('attendance summary', () => {
  it('attendanceSummaryDropsJustificationReason', () => {
    const summary = toAttendanceSummary(
      [{ id: 1, enrollmentId: 5, classDate: '2026-04-01', status: 'ABSENT', justificationReason: 'Fiebre', justificationAttachmentUrl: 'x.pdf', justifiedAt: '2026-04-02T10:00:00', justificationRejectionReason: 'Ilegible' }],
      { calculable: true, libre: false, absencePercent: 10, maxAbsencePercent: 25, extra: 'x' },
    )
    expect(summary).toEqual({
      risk: { calculable: true, libre: false, absencePercent: 10, maxAbsencePercent: 25 },
      days: [{ id: 1, classDate: '2026-04-01', status: 'ABSENT' }],
    })
    expect(JSON.stringify(summary)).not.toMatch(/Fiebre|Ilegible|pdf/)
  })
})
