export class AttendanceRequestsPage {
  constructor(page) {
    this.page = page;
    this.rejectionDialog = page.getByRole('dialog', { name: 'Rechazar justificación' });
    this.rejectionReasonInput = this.rejectionDialog.getByPlaceholder('Explicá por qué se rechaza');
    this.confirmRejectionButton = this.rejectionDialog.getByRole('button', { name: 'Rechazar', exact: true });
  }

  async open(role) {
    await this.page.goto(`/${role}/solicitudes-de-asistencia`);
  }

  justificationCard(attendanceId) {
    return this.page.getByTestId(`attendance-request-justification-${attendanceId}`);
  }

  async approveJustification(attendanceId) {
    await this.justificationCard(attendanceId).getByRole('button', { name: 'Aprobar' }).click();
  }

  async rejectJustification(attendanceId, reason) {
    await this.justificationCard(attendanceId).getByRole('button', { name: 'Rechazar' }).click();
    await this.rejectionReasonInput.fill(reason);
    await this.confirmRejectionButton.click();
  }
}
