import { useLocation } from 'react-router-dom';

export function useInvitationsBasePath() {
  const { pathname } = useLocation();
  return `/${pathname.split('/')[1]}/invitaciones`;
}
