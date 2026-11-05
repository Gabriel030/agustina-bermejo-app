export function RejectionReasonNotice({ cycle, reason, title = 'Motivo del rechazo', hint }) {
  const text = reason ?? (cycle?.status === 'REJECTED' ? cycle.rejectionReason : null);
  if (!text) return null;

  return (
    <div className="rounded-2xl bg-danger-bg px-4 py-3">
      <p className="text-[11px] font-medium uppercase tracking-wide text-danger">{title}</p>
      <p className="mt-0.5 text-[13px] text-ink">{text}</p>
      {hint && <p className="mt-1 text-[12px] text-ink-faint">{hint}</p>}
    </div>
  );
}
