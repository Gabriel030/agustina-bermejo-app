export function FirstDirectorSection({ icon: Icon, title, children }) {
  return (
    <section className="flex flex-col gap-3.5 border-border pt-5 first:pt-0 [&:not(:first-child)]:border-t">
      <h2 className="flex items-center gap-2 font-heading text-size-15 font-semibold text-ink">
        <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-accent-soft text-brand">
          <Icon className="h-4 w-4" aria-hidden="true" />
        </span>
        {title}
      </h2>
      {children}
    </section>
  );
}
