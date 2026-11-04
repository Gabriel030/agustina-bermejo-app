const CONFIRMATION_WINDOW_MS = 48 * 60 * 60 * 1000;

export function confirmationDeadlineLabel(now = new Date()) {
  const deadline = new Date(now.getTime() + CONFIRMATION_WINDOW_MS);
  const day = new Intl.DateTimeFormat('es-AR', { weekday: 'short', day: 'numeric', month: 'numeric' }).format(deadline);
  const time = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(deadline);
  return `${day} a las ${time}`;
}

export function sameEmail(a, b) {
  return (a ?? '').trim().toLowerCase() === (b ?? '').trim().toLowerCase();
}
