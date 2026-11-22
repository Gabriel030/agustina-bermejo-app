import { useEffect, useRef } from 'react';
import { Notice } from '@/components/Notice';

const BANNER_TONE_CLASS = {
  success: 'border border-success text-success-ink',
  danger: 'border border-danger text-danger-ink',
};

export function FirstDirectorBanner({ tone, icon: Icon, title, requestId, focusOnMount = false, children }) {
  const bannerRef = useRef(null);

  useEffect(() => {
    if (focusOnMount) bannerRef.current?.focus();
  }, [focusOnMount]);

  return (
    <Notice
      ref={bannerRef}
      tone={tone}
      role={tone === 'danger' ? 'alert' : 'status'}
      tabIndex={-1}
      className={`flex items-start gap-3 rounded-card px-4 py-3 text-sm ${BANNER_TONE_CLASS[tone]}`}
    >
      <Icon className="mt-0.5 h-[18px] w-[18px] shrink-0" aria-hidden="true" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <b className="font-semibold">{title}</b>
        {children && <div className="font-medium">{children}</div>}
        {requestId && <div className="text-size-12-5">Código para soporte: <b className="tabular-nums">{requestId}</b></div>}
      </div>
    </Notice>
  );
}
