import { createContext, useContext } from 'react';

export const EquivalenceActionsContext = createContext(null);

export function useEquivalenceActions() {
  return useContext(EquivalenceActionsContext);
}
