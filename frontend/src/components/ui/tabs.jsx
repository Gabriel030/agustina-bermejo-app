import * as React from "react"
import { cva } from "class-variance-authority";
import { Tabs as TabsPrimitive } from "radix-ui"

import { cn } from "@/lib/utils"

function Tabs({
  className,
  orientation = "horizontal",
  ...props
}) {
  return (
    <TabsPrimitive.Root
      data-slot="tabs"
      data-orientation={orientation}
      className={cn("group/tabs flex gap-2 data-horizontal:flex-col", className)}
      {...props} />
  );
}

const TabsVariantContext = React.createContext("default");

const tabsListVariants = cva(
  "group/tabs-list inline-flex w-fit items-center justify-center rounded-lg p-[3px] text-muted-foreground group-data-horizontal/tabs:h-8 group-data-vertical/tabs:h-fit group-data-vertical/tabs:flex-col",
  {
    variants: {
      variant: {
        default: "bg-muted",
        line: "w-full max-w-full justify-start gap-0.5 overflow-x-auto rounded-none border-b border-border bg-transparent p-0 group-data-horizontal/tabs:h-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

const DEFAULT_TRIGGER =
  "relative inline-flex h-[calc(100%-1px)] flex-1 items-center justify-center gap-1.5 rounded-md border border-transparent px-1.5 py-0.5 text-sm font-medium whitespace-nowrap text-foreground/60 transition-all group-data-vertical/tabs:w-full group-data-vertical/tabs:justify-start hover:text-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring focus-visible:outline-1 focus-visible:outline-ring disabled:pointer-events-none disabled:text-(--disabled-fg) dark:disabled:text-(--disabled-fg) has-data-[icon=inline-end]:pr-1 has-data-[icon=inline-start]:pl-1 dark:text-muted-foreground dark:hover:text-foreground data-active:bg-background data-active:text-foreground data-active:shadow-sm dark:data-active:border-input dark:data-active:bg-input/30 dark:data-active:text-foreground [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4"

const LINE_TRIGGER =
  "relative inline-flex min-h-11 min-w-11 flex-none items-center justify-center gap-1.5 whitespace-nowrap rounded-control border-b-[3px] border-transparent px-3 text-size-13 font-semibold text-ink-soft transition-colors motion-reduce:transition-none hover:bg-neutral-bg hover:text-ink focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-ring disabled:pointer-events-none disabled:text-(--disabled-fg) data-active:rounded-none data-active:border-b-(--accent) data-active:text-(--accent) data-active:hover:text-(--accent) [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4"

function TabsList({
  className,
  variant = "default",
  ...props
}) {
  return (
    <TabsVariantContext.Provider value={variant}>
      <TabsPrimitive.List
        data-slot="tabs-list"
        data-variant={variant}
        className={cn(tabsListVariants({ variant }), className)}
        {...props} />
    </TabsVariantContext.Provider>
  );
}

function TabsTrigger({
  className,
  ...props
}) {
  const variant = React.useContext(TabsVariantContext);
  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      className={cn(variant === "line" ? LINE_TRIGGER : DEFAULT_TRIGGER, className)}
      {...props} />
  );
}

function TabsContent({
  className,
  ...props
}) {
  return (
    <TabsPrimitive.Content
      data-slot="tabs-content"
      className={cn("flex-1 text-sm outline-none", className)}
      {...props} />
  );
}

export { Tabs, TabsList, TabsTrigger, TabsContent, tabsListVariants }
