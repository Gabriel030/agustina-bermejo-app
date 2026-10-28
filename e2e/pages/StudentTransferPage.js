export class StudentTransferPage {
  constructor(page) {
    this.page = page;
    this.proceedToReceiptButton = page.getByRole('button', { name: /Ya transferí/ });
    this.receiptFileInput = page.locator('input[type="file"]');
    this.sendReceiptButton = page.getByRole('button', { name: 'Enviar comprobante' });
  }

  async open(invoiceId) {
    await this.page.goto(`/alumno/pagos/${invoiceId}/transferencia`);
  }

  async uploadReceipt(file) {
    await this.proceedToReceiptButton.click();
    await this.receiptFileInput.setInputFiles(file);
    await this.sendReceiptButton.click();
  }
}
