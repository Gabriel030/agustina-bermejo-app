@ignore
Feature: Helper — crea un ciclo con fechas dadas, una comisión de la materia en ese ciclo y le asigna un titular

  Scenario:
    * url baseUrl
    * def nombreTerm = 'Ciclo QA config ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '#(startDate)', endDate: '#(endDate)' }
    When method post
    Then status 201
    * def termId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión QA config', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionId = response.id

    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201
