export const TOP_NAV_BREAKPOINTS = {
  standard: {
    barVisible: 'lg:flex',
    mobileHidden: 'lg:hidden',
    brandCollapsed: 'max-xl:[&>div:last-child]:hidden',
    nameVisible: 'xl:inline',
    nameHidden: 'xl:hidden',
    padding: 'xl:px-6',
  },
  wide: {
    barVisible: 'min-[1220px]:flex',
    mobileHidden: 'min-[1220px]:hidden',
    brandCollapsed: 'max-2xl:[&>div:last-child]:hidden',
    nameVisible: '2xl:inline',
    nameHidden: '2xl:hidden',
    padding: '2xl:px-6',
  },
};

export function topNavBreakpointFor(topNav) {
  return topNav === 'wide' ? TOP_NAV_BREAKPOINTS.wide : TOP_NAV_BREAKPOINTS.standard;
}
