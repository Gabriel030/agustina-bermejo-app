import { BACKEND_URL, PASSWORD, ROLES } from './accounts.js';

export async function loginByApi(request, role) {
  const response = await request.post(`${BACKEND_URL}/auth/login`, {
    data: { username: ROLES[role].username, password: PASSWORD },
  });
  if (!response.ok()) {
    throw new Error(`Login by API failed for ${role}: ${response.status()}`);
  }
  return response.json();
}

export class BackendApi {
  constructor(request, accessToken) {
    this.request = request;
    this.headers = { Authorization: `Bearer ${accessToken}` };
  }

  static async as(request, role) {
    const tokens = await loginByApi(request, role);
    return new BackendApi(request, tokens.accessToken);
  }

  async get(path) {
    const response = await this.request.get(`${BACKEND_URL}${path}`, { headers: this.headers });
    return { status: response.status(), body: response.ok() ? await response.json() : null };
  }

  async post(path, data) {
    const response = await this.request.post(`${BACKEND_URL}${path}`, { headers: this.headers, data });
    if (!response.ok()) {
      throw new Error(`POST ${path} failed: ${response.status()}`);
    }
    return response.json();
  }

  async put(path, data) {
    const response = await this.request.put(`${BACKEND_URL}${path}`, { headers: this.headers, data });
    if (!response.ok()) {
      throw new Error(`PUT ${path} failed: ${response.status()}`);
    }
  }

  async findOwnOpenInvoiceIds() {
    const ids = [];
    for (let candidate = 1; candidate <= 30; candidate += 1) {
      const { status, body } = await this.get(`/me/invoices/${candidate}/price-breakdown`);
      if (status === 200 && body.remainingBalance?.amount !== 0 && body.remainingBalance?.amount !== '0.00') {
        ids.push(candidate);
      }
    }
    return ids;
  }
}
