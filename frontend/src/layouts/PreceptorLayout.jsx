import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { visibleMenuItems } from '@/routes/menuAccess';

const ITEMS = menuItemsFor('preceptor');

export function PreceptorLayout() {
  return <RoleShell titulo="Preceptor" items={visibleMenuItems('preceptor', ITEMS)} />;
}
