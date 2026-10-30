import { setupServer } from 'msw/node';
import { handlers } from './handlers';
import { withContract } from '../contract/withContract';

export const API_BASE = 'http://localhost:8080';

const contractOptions = { apiBase: API_BASE };

export const server = setupServer(...handlers.map((handler) => withContract(handler, contractOptions)));

const registerHandlers = server.use.bind(server);

server.use = (...localHandlers) =>
  registerHandlers(...localHandlers.map((handler) => withContract(handler, contractOptions)));
