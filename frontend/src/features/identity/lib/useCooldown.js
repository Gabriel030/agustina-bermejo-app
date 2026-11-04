import { useCallback, useEffect, useState } from 'react';

export function useCooldown() {
  const [secondsLeft, setSecondsLeft] = useState(0);

  useEffect(() => {
    if (secondsLeft <= 0) return undefined;
    const timer = setTimeout(() => setSecondsLeft((current) => current - 1), 1000);
    return () => clearTimeout(timer);
  }, [secondsLeft]);

  const start = useCallback((seconds) => setSecondsLeft(Math.max(0, Math.ceil(seconds))), []);

  return { secondsLeft, active: secondsLeft > 0, start };
}
