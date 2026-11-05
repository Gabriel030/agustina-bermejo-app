export class StudentSubjectPage {
  constructor(page) {
    this.page = page;
    this.firstExamGrade = page
      .locator('table')
      .filter({ has: page.locator('th', { hasText: /^Parcial 1$/ }) })
      .locator('tbody td')
      .first();
    this.attendancePercentage = page.getByText(/^\d+%$/).first();
  }

  async open(sectionEnrollmentId) {
    await this.page.goto(`/alumno/mis-carreras/materias/${sectionEnrollmentId}`);
  }
}
