import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { useVisibleMenuItems } from '@/routes/useVisibleMenuItems';

const ITEMS = menuItemsFor('subdirector');

export function SubdirectorLayout() {
  const items = useVisibleMenuItems('subdirector', ITEMS);
  return <RoleShell titulo="Subdirector" items={items} />;
}
