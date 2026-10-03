Feature: Ciclos de oferta académica y disponibilidad de aulas

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    * def termName = 'Ciclo lectivo 2031 ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2031, description: '#(termName)', type: 'ANUAL', startDate: '2031-03-01T00:00:00', endDate: '2031-12-15T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def programName = 'Carrera ciclos ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(programName)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def subjectName = 'Materia ciclos ' + java.lang.System.nanoTime()
    * def subjectCode = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(subjectName)', code: '#(subjectCode)' }
    When method post
    Then status 201
    * def subjectId = response.id

    * def classroomName = 'Aula ciclos ' + java.lang.System.nanoTime()
    Given path '/classrooms'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: '#(classroomName)', capacity: 20 }
    When method post
    Then status 201
    * def classroomId = response.id

    Given path '/academic-offering-cycles'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { termId: '#(termId)', programId: '#(programId)' }
    When method post
    Then status 201
    * def cycleId = response.id

    * def slot = { classroomId: '#(classroomId)', dayOfWeek: 'THURSDAY', startTime: '08:00:00', endTime: '12:00:00' }

  Scenario: Un ciclo se consulta por id para poder retomarlo
    Given path '/academic-offering-cycles', cycleId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response contains { id: '#(cycleId)', status: 'DRAFT', termId: '#(termId)', programId: '#(programId)' }

    Given path '/academic-offering-cycles', 999999
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 404

  Scenario: Cupo mayor que la capacidad del aula — 400 con código
    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'A', subjectId: '#(subjectId)', capacity: 500, slots: ['#(slot)'] }
    When method post
    Then status 400
    And match response.code == 'SECTION_CAPACITY_EXCEEDS_CLASSROOM'

  Scenario: Un ciclo aprobado no acepta comisiones nuevas ni se reenvía, y su aula figura ocupada
    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'A', subjectId: '#(subjectId)', capacity: 20, slots: ['#(slot)'] }
    When method post
    Then status 201
    * def sectionId = response

    * def instructorId = fixtures.createInstructor()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201

    Given path '/academic-offering-cycles', cycleId, 'submit'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 204

    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'B', subjectId: '#(subjectId)', capacity: 20, slots: [] }
    When method post
    Then status 409
    And match response.code == 'CYCLE_NOT_EDITABLE'

    Given path '/academic-offering-cycles', cycleId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    When method post
    Then status 204

    Given path '/academic-offering-cycles', cycleId, 'submit'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 409
    And match response.code == 'CYCLE_NOT_EDITABLE'

    Given path '/academic-offering-cycles', cycleId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response.status == 'APPROVED'

    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'C', subjectId: '#(subjectId)', capacity: 20, slots: [] }
    When method post
    Then status 409
    And match response.code == 'CYCLE_NOT_EDITABLE'

    Given path '/terms', termId, 'classrooms', 'availability'
    And header Authorization = 'Bearer ' + preceptorToken
    And params { dayOfWeek: 'THURSDAY', startTime: '08:00', endTime: '12:00' }
    When method get
    Then status 200
    * def usedClassroom = karate.filter(response, function(c){ return c.id == classroomId })
    And match usedClassroom[0].available == false

  Scenario: Un ciclo rechazado muestra el motivo, se corrige y se reenvía
    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'A', subjectId: '#(subjectId)', capacity: 20, slots: ['#(slot)'] }
    When method post
    Then status 201
    * def sectionId = response

    * def instructorId = fixtures.createInstructor()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201

    Given path '/academic-offering-cycles', cycleId, 'submit'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 204

    Given path '/academic-offering-cycles', cycleId, 'reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'Falta la comisión del turno noche' }
    When method post
    Then status 204

    Given path '/academic-offering-cycles', cycleId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response contains { status: 'REJECTED', rejectionReason: 'Falta la comisión del turno noche' }

    * def eveningSlot = { classroomId: '#(classroomId)', dayOfWeek: 'THURSDAY', startTime: '18:00:00', endTime: '20:00:00' }
    Given path '/academic-offering-cycles', cycleId, 'sections'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { name: 'Noche', subjectId: '#(subjectId)', capacity: 20, slots: ['#(eveningSlot)'] }
    When method post
    Then status 201
    * def eveningSectionId = response

    Given path '/program-sections', eveningSectionId, 'instructors'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { instructorId: '#(instructorId)', role: 'LEAD' }
    When method post
    Then status 201

    Given path '/academic-offering-cycles', cycleId, 'submit'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 204

    Given path '/academic-offering-cycles', cycleId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response.status == 'PENDING_APPROVAL'
    And match response.rejectionReason == null

  Scenario: Disponibilidad de un período inexistente — 404
    Given path '/terms', 999999, 'classrooms', 'availability'
    And header Authorization = 'Bearer ' + preceptorToken
    And params { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '12:00' }
    When method get
    Then status 404
