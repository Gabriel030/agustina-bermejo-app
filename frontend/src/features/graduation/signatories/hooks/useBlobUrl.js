import { useEffect, useState } from 'react';

export function useBlobUrl(blob) {
  const [created, setCreated] = useState({ blob: null, url: null });
  useEffect(() => {
    if (!blob) return undefined;
    let active = true;
    const url = URL.createObjectURL(blob);
    queueMicrotask(() => {
      if (active) setCreated({ blob, url });
    });
    return () => {
      active = false;
      URL.revokeObjectURL(url);
    };
  }, [blob]);
  return blob && created.blob === blob ? created.url : null;
}
