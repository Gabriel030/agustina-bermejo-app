import * as React from "react"
import { Eye, EyeOff } from "lucide-react"

import { cn } from "@/lib/utils"
import { Input } from "@/components/ui/input"

/*
 * Variante de Input para contraseñas: ícono de ojo que alterna entre
 * type="password"/"text" — pedido explícito del usuario para Login y
 * cualquier otro campo de contraseña que se agregue después (reset,
 * cambio de contraseña en Opciones). Vive en components/ui/ porque es una
 * variante genérica de la primitiva Input, no algo específico de una
 * pantalla — ver docs/context/frontend.md.
 */
function PasswordInput({ className, disabled, ...props }) {
  const [visible, setVisible] = React.useState(false)

  return (
    <div className="relative">
      <Input
        type={visible ? "text" : "password"}
        disabled={disabled}
        className={cn("pr-8", className)}
        {...props}
      />
      <button
        type="button"
        tabIndex={-1}
        disabled={disabled}
        aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
        aria-pressed={visible}
        onClick={() => setVisible((v) => !v)}
        className="absolute inset-y-0 right-2 flex items-center text-ink-faint transition-colors hover:text-ink-soft disabled:pointer-events-none disabled:opacity-50"
      >
        {visible ? <EyeOff size={16} /> : <Eye size={16} />}
      </button>
    </div>
  )
}

export { PasswordInput }
