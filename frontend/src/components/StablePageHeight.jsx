import { useLayoutEffect, useRef, useState } from 'react';

export function StablePageHeight({ resetKey, className, children }) {
  const ref = useRef(null);
  const [reserved, setReserved] = useState({ key: resetKey, height: 0 });
  const height = reserved.key === resetKey ? reserved.height : 0;

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => {
      const measured = entry.borderBoxSize?.[0]?.blockSize ?? element.offsetHeight;
      setReserved((current) =>
        current.key === resetKey ? { key: resetKey, height: Math.max(current.height, measured) } : { key: resetKey, height: measured },
      );
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [resetKey]);

  return (
    <div ref={ref} className={className} style={height ? { minHeight: `${height}px` } : undefined}>
      {children}
    </div>
  );
}
