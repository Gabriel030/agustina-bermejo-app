import * as React from "react"
import { cn } from "@/lib/utils"
import { useScrollEdgeMask } from "@/hooks/useScrollEdgeMask"

function fadeState({ start, end }) {
  if (start && end) return "both"
  if (start) return "start"
  if (end) return "end"
  return "none"
}

function Table({
  className,
  containerClassName,
  containerLabel = "Tabla con desplazamiento horizontal",
  ...props
}) {
  const containerRef = React.useRef(null)
  const fadeEnabled = !containerClassName?.includes("overflow-visible")
  const { overflow, maskImage } = useScrollEdgeMask(containerRef, fadeEnabled)
  const scrollable = fadeEnabled && (overflow.start || overflow.end)
  return (
    <div
      ref={containerRef}
      data-slot="table-container"
      className={cn(
        "relative w-full overflow-x-auto",
        scrollable && "focus-visible:outline-2 focus-visible:outline-solid focus-visible:-outline-offset-2 focus-visible:outline-(--focus-ring)",
        containerClassName
      )}
      data-fade={fadeEnabled ? fadeState(overflow) : "none"}
      style={maskImage ? { maskImage, WebkitMaskImage: maskImage } : undefined}
      {...(scrollable ? { tabIndex: 0, role: "region", "aria-label": containerLabel } : {})}>
      <table
        data-slot="table"
        className={cn("w-full caption-bottom text-sm", className)}
        {...props} />
    </div>
  );
}

function TableHeader({
  className,
  ...props
}) {
  return (
    <thead
      data-slot="table-header"
      className={cn("sticky top-0 z-10 bg-(--table-head-bg) [&_tr]:border-b", className)}
      {...props} />
  );
}

function TableBody({
  className,
  ...props
}) {
  return (
    <tbody
      data-slot="table-body"
      className={cn("[&_tr:last-child]:border-0", className)}
      {...props} />
  );
}

function TableFooter({
  className,
  ...props
}) {
  return (
    <tfoot
      data-slot="table-footer"
      className={cn("border-t bg-muted/50 font-medium [&>tr]:last:border-b-0", className)}
      {...props} />
  );
}

function TableRow({
  className,
  ...props
}) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        "border-b transition-colors hover:bg-accent-soft has-aria-expanded:bg-accent-soft data-[state=selected]:bg-accent-soft",
        className
      )}
      {...props} />
  );
}

function TableHead({
  className,
  ...props
}) {
  return (
    <th
      data-slot="table-head"
      className={cn(
        "h-10 px-2 text-left align-middle text-(length:--table-head-size) font-bold tracking-wide text-muted-foreground uppercase whitespace-nowrap [&:has([role=checkbox])]:pr-0",
        className
      )}
      {...props} />
  );
}

function TableCell({
  className,
  ...props
}) {
  return (
    <td
      data-slot="table-cell"
      className={cn(
        "p-2 align-middle whitespace-nowrap [&:has([role=checkbox])]:pr-0",
        className
      )}
      {...props} />
  );
}

function TableCaption({
  className,
  ...props
}) {
  return (
    <caption
      data-slot="table-caption"
      className={cn("mt-4 text-sm text-muted-foreground", className)}
      {...props} />
  );
}

export {
  Table,
  TableHeader,
  TableBody,
  TableFooter,
  TableHead,
  TableRow,
  TableCell,
  TableCaption,
}
