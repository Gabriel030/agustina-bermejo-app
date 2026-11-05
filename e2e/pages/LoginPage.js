import { PASSWORD, ROLES } from '../fixtures/accounts.js';

export class LoginPage {
  constructor(page) {
    this.page = page;
    this.identifierInput = page.getByPlaceholder('DNI o email');
    this.passwordInput = page.getByPlaceholder('••••••••');
    this.submitButton = page.getByRole('button', { name: 'Ingresar' });
  }

  async open() {
    await this.page.goto('/login');
  }

  async signInAs(role) {
    await this.identifierInput.fill(ROLES[role].username);
    await this.passwordInput.fill(PASSWORD);
    await this.submitButton.click();
  }
}
