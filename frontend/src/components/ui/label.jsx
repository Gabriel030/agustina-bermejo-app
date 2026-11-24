"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { Label as LabelPrimitive } from "radix-ui"

function Label({
  className,
  ...props
}) {
  return (
    <LabelPrimitive.Root
      data-slot="label"
      className={cn(
        "flex items-center gap-2 text-sm leading-none font-medium select-none group-data-[disabled=true]:pointer-events-none group-data-[disabled=true]:text-(--disabled-fg) peer-disabled:cursor-not-allowed peer-disabled:text-(--disabled-fg)",
        className
      )}
      {...props}
    />
  )
}

export { Label }
