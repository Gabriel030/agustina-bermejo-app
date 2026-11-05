import { afterEach } from 'vitest';
import { takeContractDeviations } from './withContract';

export const GRADUATION_DEBT_SUMMARY_PENDING_IN_CONTRACT = /^GET \/graduation-requests\/\d+\/debt-summary$/;

export function tolerateOperationsPendingInContract(pattern) {
  afterEach(() => {
    const deviations = takeContractDeviations();
    const remaining = deviations.filter((deviation) => !pattern.test(deviation.key));
    const tolerated = deviations.filter((deviation) => pattern.test(deviation.key));
    const unexpected = tolerated.filter((deviation) => deviation.violations.some((violation) => violation !== 'operation not found in the contract'));
    if (remaining.length > 0 || unexpected.length > 0) {
      throw new Error(`Mocks deviate from contracts/openapi.json:\n${JSON.stringify([...remaining, ...unexpected])}`);
    }
  });
}
