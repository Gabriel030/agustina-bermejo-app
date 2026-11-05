export class AdministrativeBillingPage {
  constructor(page) {
    this.page = page;
    this.approveButton = page.getByRole('button', { name: 'Aprobar' });
    this.rejectButton = page.getByRole('button', { name: 'Rechazar' });
    this.rejectionDialog = page.getByRole('dialog');
    this.rejectionReasonInput = this.rejectionDialog.getByPlaceholder(/por qué se rechaza/);
    this.confirmRejectionButton = this.rejectionDialog.getByRole('button', { name: 'Rechazar', exact: true });
  }

  async openReceipts() {
    await this.page.goto('/administrativo/facturacion?tab=comprobantes');
  }

  async rejectReceipt(reason) {
    await this.rejectButton.click();
    await this.rejectionReasonInput.fill(reason);
    await this.confirmRejectionButton.click();
  }
}
