import { useLocation } from 'react-router-dom';

export function useReviewBasePath() {
  const { pathname } = useLocation();
  return `/${pathname.split('/')[1]}`;
}
