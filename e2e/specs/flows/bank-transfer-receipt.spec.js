import { test, expect } from '@playwright/test';
import { BackendApi } from '../../fixtures/api.js';
import { LoginPage } from '../../pages/LoginPage.js';
import { StudentTransferPage } from '../../pages/StudentTransferPage.js';
import { AdministrativeBillingPage } from '../../pages/AdministrativeBillingPage.js';

const RECEIPT_FILE = {
  name: 'comprobante.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n'),
};

test.describe.configure({ mode: 'serial' });

test.describe('@flow bank transfer receipt', () => {
  let openInvoiceIds;

  test.beforeAll(async ({ request }) => {
    const administrative = await BackendApi.as(request, 'administrativo');
    await administrative.put('/payment-config', {
      bankAlias: 'escuela.e2e',
      bankCbu: '0170099220000067797370',
      accountHolder: 'Instituto E2E',
      mpEnabled: false,
    });
    const student = await BackendApi.as(request, 'alumno');
    openInvoiceIds = await student.findOwnOpenInvoiceIds();
    expect(openInvoiceIds.length).toBeGreaterThanOrEqual(2);
  });

  async function submitReceiptAsStudent(browser, invoiceId) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInAs('alumno');
    await expect(page).toHaveURL(/\/alumno\/inicio$/);
    const transferPage = new StudentTransferPage(page);
    await transferPage.open(invoiceId);
    await expect(page.getByText('escuela.e2e')).toBeVisible();
    await transferPage.uploadReceipt(RECEIPT_FILE);
    await expect(page.getByText('Pendiente de revisión')).toBeVisible();
    await context.close();
  }

  async function reviewAsAdministrative(browser, reviewAction) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.signInAs('administrativo');
    await expect(page).toHaveURL(/\/administrativo\/inicio$/);
    const billingPage = new AdministrativeBillingPage(page);
    await billingPage.openReceipts();
    await expect(page.getByText('Alumno Demo')).toBeVisible();
    await reviewAction(page, billingPage);
    await context.close();
  }

  test('bankTransferReceiptApprovedMarksInvoicePaid', async ({ browser, request }) => {
    const invoiceId = openInvoiceIds[0];

    await submitReceiptAsStudent(browser, invoiceId);
    await reviewAsAdministrative(browser, async (page, billingPage) => {
      await billingPage.approveButton.click();
      await expect(page.getByText('Comprobante aprobado')).toBeVisible();
    });

    const student = await BackendApi.as(request, 'alumno');
    await expect
      .poll(async () => (await student.get(`/me/invoices/${invoiceId}/price-breakdown`)).body.status)
      .toBe('PAID');
  });

  test('bankTransferReceiptRejectedKeepsInvoicePending', async ({ browser, request }) => {
    const invoiceId = openInvoiceIds[1];

    await submitReceiptAsStudent(browser, invoiceId);
    await reviewAsAdministrative(browser, async (page, billingPage) => {
      await billingPage.rejectReceipt('El monto no coincide');
      await expect(page.getByText('Sin comprobantes pendientes')).toBeVisible();
    });

    const student = await BackendApi.as(request, 'alumno');
    const { body } = await student.get(`/me/invoices/${invoiceId}/price-breakdown`);
    expect(body.status).not.toBe('PAID');
    const receipts = await student.get('/me/bank-transfer-receipts');
    expect(receipts.body.find((receipt) => receipt.invoiceId === invoiceId).status).toBe('REJECTED');
  });
});
