export function RejectionReasonNotice({ cycle }) {
  if (cycle?.status !== 'REJECTED' || !cycle.rejectionReason) return null;

  return (
    <div className="rounded-2xl bg-danger-bg px-4 py-3">
      <p className="text-[11px] font-medium uppercase tracking-wide text-danger">Motivo del rechazo</p>
      <p className="mt-0.5 text-[13px] text-ink">{cycle.rejectionReason}</p>
    </div>
  );
}
