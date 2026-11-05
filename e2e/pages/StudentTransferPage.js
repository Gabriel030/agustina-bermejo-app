export class StudentTransferPage {
  constructor(page) {
    this.page = page;
    this.proceedToReceiptButton = page.getByRole('button', { name: /Ya transferí/ });
    this.receiptFileInput = page.locator('input[type="file"]');
    this.operationNumberInput = page.getByLabel('Número de operación');
    this.originAccountInput = page.getByLabel('CBU o alias de origen');
    this.holderNameInput = page.getByLabel('Titular de la cuenta de origen');
    this.sendReceiptButton = page.getByRole('button', { name: 'Enviar comprobante' });
  }

  async open(invoiceId) {
    await this.page.goto(`/alumno/pagos/${invoiceId}/transferencia`);
  }

  async openReceiptStep() {
    await this.proceedToReceiptButton.click();
  }

  async fillReceipt(file, { operationNumber, originAccount }) {
    await this.receiptFileInput.setInputFiles(file);
    await this.operationNumberInput.fill(operationNumber);
    await this.originAccountInput.fill(originAccount);
  }
}
