const DAY_LABELS = {
  MONDAY: 'Lun', TUESDAY: 'Mar', WEDNESDAY: 'Mié',
  THURSDAY: 'Jue', FRIDAY: 'Vie', SATURDAY: 'Sáb', SUNDAY: 'Dom',
};

export function describeSection(section) {
  if (!section) return null;
  const lead = section.instructors?.find((instructor) => instructor.role === 'LEAD');
  const slot = section.slots?.[0];
  return [
    section.subjectName,
    section.name,
    slot && `${DAY_LABELS[slot.dayOfWeek] ?? ''} ${slot.startTime?.slice(0, 5) ?? ''}`.trim(),
    lead && `Prof. ${lead.name}`,
  ]
    .filter(Boolean)
    .join(' · ');
}
