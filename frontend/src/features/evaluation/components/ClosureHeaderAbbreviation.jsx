export function ClosureHeaderAbbreviation({ short, full }) {
  return (
    <abbr title={full} className="no-underline">
      {short}
    </abbr>
  );
}
