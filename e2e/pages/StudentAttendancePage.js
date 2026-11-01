export class StudentAttendancePage {
  constructor(page) {
    this.page = page;
    this.justifyDialog = page.getByRole('dialog', { name: /justificar/i });
    this.receiptFileInput = this.justifyDialog.locator('input[type="file"]');
    this.confirmJustificationButton = this.justifyDialog.getByRole('button', { name: 'Justificar', exact: true });
  }

  async open(sectionEnrollmentId) {
    await this.page.goto(`/alumno/mis-carreras/materias/${sectionEnrollmentId}`);
  }

  absentDay(dayOfMonth) {
    return this.page.getByRole('button', { name: String(dayOfMonth), exact: true }).and(this.page.getByTitle('Justificar'));
  }

  async openAbsence(dayOfMonth) {
    await this.absentDay(dayOfMonth).click();
  }

  async justifyAbsence(dayOfMonth, receiptFile) {
    await this.openAbsence(dayOfMonth);
    await this.receiptFileInput.setInputFiles(receiptFile);
    await this.confirmJustificationButton.click();
  }
}
