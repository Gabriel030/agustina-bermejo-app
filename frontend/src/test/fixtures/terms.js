export function termFixture(overrides = {}) {
  return {
    id: 1,
    year: 2027,
    description: 'Ciclo lectivo 2027',
    type: 'ANNUAL',
    status: 'ACTIVE',
    startDate: '2027-03-01T00:00:00',
    endDate: '2027-11-30T23:59:59',
    enrollmentStart: '2027-02-01T00:00:00',
    enrollmentEnd: '2027-02-28T23:59:59',
    enrollmentOpen: false,
    ...overrides,
  };
}
