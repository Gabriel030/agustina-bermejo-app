export const STALE_TIME = Object.freeze({
  CRITICAL: 0,
  OPERATIONAL: 30_000,
  MEDIUM: 60_000,
  LONG: 5 * 60_000,
  STATIC: 60 * 60_000,
})

export const POLL_INTERVAL = Object.freeze({
  NOTIFICATIONS: 15_000,
  ANNOUNCEMENTS: 60_000,
  SCHEDULE: 5 * 60_000,
})

export function pollWhenOnline(intervalMs) {
  return () => (navigator.onLine ? intervalMs : false)
}

export function pollingOptions(intervalMs, staleTime) {
  return {
    staleTime,
    refetchInterval: pollWhenOnline(intervalMs),
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  }
}

const KEYS_BY_STALE_TIME = [
  [STALE_TIME.CRITICAL, [
    ['me'],
    ['students'],
    ['payments'],
    ['payment-config'],
    ['authorization-requests'],
    ['monthly-runs'],
    ['billing-runs'],
    ['student-discounts'],
    ['annual-upfront'],
    ['financial-dashboard'],
    ['attendance'],
    ['director-approval-requests'],
    ['program-enrollments'],
    ['staff-grade-grid'],
    ['section-evaluation-configs'],
    ['exam-boards'],
  ]],
  [STALE_TIME.MEDIUM, [
    ['me', 'schedule'],
    ['me', 'announcements'],
    ['me', 'notification-preferences'],
    ['me', 'avatar'],
  ]],
  [STALE_TIME.LONG, [
    ['catalog'],
    ['programs'],
    ['programas'],
    ['cursos'],
    ['admin-courses'],
    ['subjects'],
    ['program-subjects'],
    ['course-subjects'],
    ['prerequisites'],
    ['corequisites'],
    ['program-elective-groups'],
    ['ciclos'],
    ['terms'],
    ['classrooms'],
    ['evaluation-schemes'],
    ['instructors'],
  ]],
  [STALE_TIME.STATIC, [['holidays']]],
]

export function applyCachePolicy(client) {
  KEYS_BY_STALE_TIME.forEach(([staleTime, keys]) => {
    keys.forEach((queryKey) => client.setQueryDefaults(queryKey, { staleTime }))
  })
}
