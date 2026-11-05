import { useMyModules } from '@/hooks/useMyModules';
import { menuNeedsModules, visibleMenuItems } from '@/routes/menuAccess';

export function useVisibleMenuItems(role, items) {
  const modules = useMyModules({ enabled: menuNeedsModules(role, items) });
  return visibleMenuItems(role, items, { billingEnabled: modules.billingEnabled });
}
