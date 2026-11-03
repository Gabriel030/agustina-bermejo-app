import { UserPlus } from 'lucide-react';
import { RoleShell } from '@/layouts/RoleShell';

const ITEMS = [{ to: '/platform_admin/primer-director', label: 'Primer Director', icon: UserPlus }];

export function PlatformLayout() {
  return <RoleShell titulo="Plataforma" items={ITEMS} />;
}
