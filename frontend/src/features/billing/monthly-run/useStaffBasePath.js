import { useLocation } from 'react-router-dom';

export function useStaffBasePath() {
  const { pathname } = useLocation();
  return `/${pathname.split('/')[1]}`;
}
