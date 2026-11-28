import { describe, expect, it } from 'vitest';
import {
  browsableTasks,
  isVisibleStatus,
  parseHelpTask,
  searchTasks,
  tasksForRole,
} from '@/features/help/lib/helpTasks';
import { canReadHelp } from '@/lib/helpAccess';

const SOURCE = `---
title: Abrir una cohorte
section: set-up-school-year
roles: [preceptor, director]
status: publicado
screens: ["oferta-academica/nuevo-ciclo"]
last_review: 2026-11-28
---

## Para qué sirve

Crear la **cohorte** de un año lectivo.

## Pasos

1. Entrá a Oferta académica.
`;

const task = (overrides) => parseHelpTask('../content/students/x.md', SOURCE.replace('Abrir una cohorte', overrides.title ?? 'Abrir una cohorte'));

describe('parseHelpTask', () => {
  it('readsFrontMatterAndBuildsIdFromSectionAndFileName', () => {
    const parsed = parseHelpTask('../content/set-up-school-year/open-cohort.md', SOURCE);

    expect(parsed).toMatchObject({
      id: 'set-up-school-year/open-cohort',
      title: 'Abrir una cohorte',
      sectionLabel: 'Armar el año',
      roles: ['preceptor', 'director'],
      status: 'publicado',
    });
    expect(parsed.text).toContain('Crear la cohorte');
  });
});

describe('tasksForRole', () => {
  const tasks = [
    { id: 'a', roles: ['preceptor'] },
    { id: 'b', roles: ['director'] },
    { id: 'c', roles: ['preceptor', 'director'] },
  ];

  it('keepsOnlyTasksThatIncludeTheRole', () => {
    expect(tasksForRole(tasks, 'preceptor', false).map((item) => item.id)).toEqual(['a', 'c']);
  });

  it('keepsEveryTaskWhenAllRolesAreRequested', () => {
    expect(tasksForRole(tasks, 'preceptor', true)).toHaveLength(3);
  });
});

describe('isVisibleStatus', () => {
  it('hidesDraftsWhenDraftsAreNotIncluded', () => {
    expect(isVisibleStatus({ status: 'borrador' }, false)).toBe(false);
    expect(isVisibleStatus({ status: 'publicado' }, false)).toBe(true);
  });

  it('showsDraftsWhenDraftsAreIncluded', () => {
    expect(isVisibleStatus({ status: 'borrador' }, true)).toBe(true);
  });
});

describe('browsableTasks', () => {
  it('leavesOutTheSectionUnderRedesign', () => {
    const tasks = [{ section: 'billing' }, { section: 'students' }];

    expect(browsableTasks(tasks)).toEqual([{ section: 'students' }]);
  });
});

describe('searchTasks', () => {
  const tasks = [task({ title: 'Cerrar comisión' }), task({ title: 'Tomar asistencia' })];

  it('matchesTitleIgnoringAccentsAndCase', () => {
    expect(searchTasks(tasks, 'COMISION').map(({ task: found }) => found.title)).toEqual(['Cerrar comisión']);
  });

  it('matchesBodyTextIgnoringAccents', () => {
    expect(searchTasks(tasks, 'academica')).toHaveLength(2);
  });

  it('returnsNothingForBlankQueryAndForUnknownWords', () => {
    expect(searchTasks(tasks, '   ')).toEqual([]);
    expect(searchTasks(tasks, 'zzzz')).toEqual([]);
  });
});

describe('canReadHelp', () => {
  it.each(['docente', 'preceptor', 'administrativo', 'subdirector', 'director', 'platform_admin'])('allows %s', (role) => {
    expect(canReadHelp(role)).toBe(true);
  });

  it('deniesStudentsAndUnknownRoles', () => {
    expect(canReadHelp('alumno')).toBe(false);
    expect(canReadHelp(undefined)).toBe(false);
  });
});
