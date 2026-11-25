import * as React from "react"

const EDGE_PX = 28

export function useScrollEdgeMask(containerRef, enabled = true, remeasureKey) {
  const [overflow, setOverflow] = React.useState({ start: false, end: false })

  React.useEffect(() => {
    const container = containerRef.current
    if (!enabled || !container) {
      setOverflow((previous) => (previous.start || previous.end ? { start: false, end: false } : previous))
      return undefined
    }
    function measureOverflow() {
      const start = container.scrollLeft > 1
      const end = container.scrollLeft + container.clientWidth < container.scrollWidth - 1
      setOverflow((previous) => (previous.start === start && previous.end === end ? previous : { start, end }))
    }
    measureOverflow()
    container.addEventListener("scroll", measureOverflow, { passive: true })
    window.addEventListener("resize", measureOverflow)
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(measureOverflow)
    if (observer) {
      observer.observe(container)
      if (container.firstElementChild) observer.observe(container.firstElementChild)
    }
    return () => {
      container.removeEventListener("scroll", measureOverflow)
      window.removeEventListener("resize", measureOverflow)
      if (observer) observer.disconnect()
    }
  }, [enabled, containerRef, remeasureKey])

  const hasOverflow = overflow.start || overflow.end
  const maskImage = enabled && hasOverflow
    ? `linear-gradient(to right, ${overflow.start ? `transparent, black ${EDGE_PX}px` : "black, black"}, ${overflow.end ? `black calc(100% - ${EDGE_PX}px), transparent` : "black, black"})`
    : undefined

  return { overflow, maskImage }
}
