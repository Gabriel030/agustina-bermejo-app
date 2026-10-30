import { setupServer } from 'msw/node';
import { handlers } from './handlers';
import { withContract } from '../contract/withContract';

export const API_BASE = 'http://localhost:8080';

export const server = setupServer(...handlers.map((handler) => withContract(handler, { apiBase: API_BASE })));
