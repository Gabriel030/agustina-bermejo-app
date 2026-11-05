import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { useVisibleMenuItems } from '@/routes/useVisibleMenuItems';

const ITEMS = menuItemsFor('director');

export function DirectorLayout() {
  const items = useVisibleMenuItems('director', ITEMS);
  return <RoleShell titulo="Director" items={items} />;
}
