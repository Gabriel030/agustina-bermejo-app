import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { myModulesQuery } from '@/hooks/useMyModules';
import { ROLE_CONFIG } from '@/routes/roleConfig';
import { firstRealPage } from '@/routes/menuAccess';
import { preloadRolePage } from '@/routes/preloadPage';

export function useStartupPrefetch(usuario) {
  const queryClient = useQueryClient();
  const { pathname } = useLocation();
  const userId = usuario?.id;
  const role = usuario?.roles?.[0]?.toLowerCase();

  useEffect(() => {
    if (!userId || !role) return;
    queryClient.prefetchQuery(myModulesQuery);
  }, [userId, role, queryClient]);

  useEffect(() => {
    if (!userId || !role) return;
    ROLE_CONFIG[role]?.Layout?.preload?.();
    if (pathname.startsWith(`/${role}/`)) {
      preloadRolePage(role, pathname);
      return;
    }
    const pages = ROLE_CONFIG[role]?.pages;
    const landing = pages ? firstRealPage(role, pages) : null;
    if (landing) preloadRolePage(role, `/${role}/${landing}`);
  }, [userId, role, pathname]);
}
