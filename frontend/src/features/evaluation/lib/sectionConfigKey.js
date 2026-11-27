export function sectionConfigKind(config) {
  return config.sectionKind === 'COURSE' ? 'COURSE' : 'PROGRAM';
}

export function sectionConfigSectionId(config) {
  return sectionConfigKind(config) === 'COURSE' ? config.courseSectionId : config.subjectSectionId;
}

export function sectionConfigKey(config) {
  return `${sectionConfigKind(config)}-${sectionConfigSectionId(config)}`;
}
