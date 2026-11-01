export class StudentCareerPage {
  constructor(page) {
    this.page = page;
    this.startEnrollingButton = page.getByRole('button', { name: /Inscribirme a materias/ });
    this.confirmEnrollmentButton = page.getByRole('button', { name: 'Confirmar inscripción' });
    this.backToSubjectsButton = page.getByRole('button', { name: /Volver a mis materias/ });
  }

  async open(programEnrollmentId, tab = 'materias') {
    await this.page.goto(`/alumno/mis-carreras?carrera=${programEnrollmentId}&tab=${tab}`);
  }

  async enrollInSection(subjectName, sectionName) {
    await this.startEnrollingButton.click();
    await this.page.getByRole('button', { name: subjectName }).click();
    await this.page.getByRole('button', { name: sectionName }).click();
    await this.confirmEnrollmentButton.click();
  }

  enrolledSubjectRow(subjectName) {
    return this.page.getByRole('button', { name: new RegExp(subjectName) });
  }
}
