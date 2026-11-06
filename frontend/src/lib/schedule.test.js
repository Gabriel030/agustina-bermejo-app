import { describe, expect, it } from 'vitest'
import { findNextClass, isScheduledToday, parseSchedule } from './schedule'
import { FIXED_NOW, installFixedClock } from '@/test/fixedClock'

installFixedClock()

describe('schedule parser', () => {
  it('parsesDaysAndStartTimeFromFreeText', () => {
    const parsed = parseSchedule('Lunes y miércoles de 18:30 a 20:30')
    expect([...parsed.days].sort()).toEqual([1, 3])
    expect(parsed.hour).toBe(18)
    expect(parsed.minute).toBe(30)
  })

  it('expandsDayRanges', () => {
    expect([...parseSchedule('Lun a vie 9hs').days].sort()).toEqual([1, 2, 3, 4, 5])
  })

  it('returnsNullWhenTheTextCannotBeInterpreted', () => {
    expect(parseSchedule('A coordinar con el docente')).toBeNull()
    expect(parseSchedule('')).toBeNull()
    expect(parseSchedule(null)).toBeNull()
  })

  it('detectsTodayFromTheClientClock', () => {
    const now = new Date(FIXED_NOW)
    expect(isScheduledToday('Jueves 18:00', now)).toBe(true)
    expect(isScheduledToday('Viernes 18:00', now)).toBe(false)
  })

  it('findsTheNextClassSkippingPastOnesAndUnparsableItems', () => {
    const now = new Date(FIXED_NOW)
    const items = [
      { sectionEnrollmentId: 1, schedule: 'Jueves 09:00' },
      { sectionEnrollmentId: 2, schedule: 'Sin definir' },
      { sectionEnrollmentId: 3, schedule: 'Viernes 08:00' },
    ]
    const next = findNextClass(items, now)
    expect(next.item.sectionEnrollmentId).toBe(3)
    expect(next.offset).toBe(1)
  })

  it('returnsNullWhenNothingIsParsable', () => {
    expect(findNextClass([{ schedule: 'x' }], new Date(FIXED_NOW))).toBeNull()
  })
})
