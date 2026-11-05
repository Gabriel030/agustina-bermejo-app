import { RoleShell } from '@/layouts/RoleShell';
import { menuItemsFor } from '@/routes/roleMenu';
import { visibleMenuItems } from '@/routes/menuAccess';

const ITEMS = menuItemsFor('platform_admin');

export function PlatformLayout() {
  return <RoleShell titulo="Plataforma" items={visibleMenuItems('platform_admin', ITEMS)} />;
}
