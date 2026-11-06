import { Bell, Clock, Eye, Info, Mail, TriangleAlert } from 'lucide-react';
import { fechaEnPalabras, textoDeAlcance } from '@/lib/avisos';

function Linea({ icono: Icono, children }) {
  return (
    <div className="flex items-start gap-2.5 text-[12.5px] leading-relaxed text-ink-soft">
      <Icono className="mt-0.5 h-3.5 w-3.5 shrink-0 text-ink-faint" aria-hidden="true" />
      <span>{children}</span>
    </div>
  );
}

export function TiraDeConsecuencias({ alcanzados, total, sinCorregir, scheduledFor, reNotifica, sinNotificar, urgent = false, emailEnabled = false }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-neutral-bg/55 px-4 py-3">
      {total > 0 && (
        <Linea icono={Eye}>
          <span className="font-bold text-ink">{textoDeAlcance(alcanzados, total)}</span>
        </Linea>
      )}
      {sinCorregir && (
        <Linea icono={TriangleAlert}>
          Hay instancias sin corregir: quien todavía no tiene nota <span className="font-bold text-ink">no entra</span>{' '}
          en el grupo.
        </Linea>
      )}
      {scheduledFor && (
        <Linea icono={Clock}>
          Sale el <span className="font-bold text-ink">{fechaEnPalabras(scheduledFor)}</span>.
        </Linea>
      )}
      {reNotifica && (
        <Linea icono={Bell}>
          Al guardar, <span className="font-bold text-ink">los mismos destinatarios</span> vuelven a recibir la
          notificación.
        </Linea>
      )}
      {!sinNotificar && (
        <Linea icono={Mail}>
          {urgent ? (
            <>
              <span className="font-bold text-ink">También por email.</span> Los avisos urgentes siempre salen por email
              a quienes tienen el correo verificado.
            </>
          ) : emailEnabled ? (
            <>
              <span className="font-bold text-ink">También por email</span> a quienes tienen el correo verificado.
            </>
          ) : (
            <>Solo por campana: tu instituto no envía por email los avisos que no son urgentes.</>
          )}
        </Linea>
      )}
      {sinNotificar && (
        <Linea icono={Info}>
          <span className="font-bold text-ink">Nadie recibe una notificación.</span> Los alumnos ven el cambio cuando
          vuelvan a abrir el aviso.
        </Linea>
      )}
    </div>
  );
}
