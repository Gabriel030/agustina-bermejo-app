export function multiSelectFilter(row, columnId, selectedValues) {
  if (!Array.isArray(selectedValues)) return true;
  return selectedValues.includes(row.getValue(columnId));
}

multiSelectFilter.autoRemove = (value) => !Array.isArray(value);
