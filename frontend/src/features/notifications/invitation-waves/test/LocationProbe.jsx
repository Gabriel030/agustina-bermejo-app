import { useLocation } from 'react-router-dom';

export function LocationProbe() {
  const { pathname, search } = useLocation();
  return <div data-testid="location-probe">{`${pathname}${search}`}</div>;
}
