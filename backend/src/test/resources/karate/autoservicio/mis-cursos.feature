Feature: Caso de uso — Mis cursos (MeCourseEnrollmentController)

  # Épica 2 Fase 1 (Alumno · Inicio/Mis cursos): faltaba el equivalente de autoservicio
  # de GET /students/{id}/course-enrollments (uso staff, ya existía) — el alumno
  # necesita listar sus propios CourseEnrollment ya aprobados (no las solicitudes
  # pendientes, esas viven en /me/course-enrollments vía POST multipart, Épica 7 Fase 3).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.createInstitution()
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def descripcionPeriodo = 'Karate - Periodo mis cursos ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodo)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombreCurso = 'Karate - Curso mis cursos ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', schedule: 'Sáb 10-13hs', capacity: 20, price: { amount: '15000', currency: 'ARS' }, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    * def alumno = fixtures.createConfirmedStudent(institutionId)
    Given path '/students', alumno.studentId, 'course-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { courseId: '#(courseId)' }
    When method post
    Then status 201
    * def courseEnrollmentId = response.id

  Scenario: Listo mis propios cursos, no los de otro alumno
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def propio = response.filter(function(x){ return x.id == courseEnrollmentId })[0]
    And match propio.courseId == courseId

    * def otroAlumno = fixtures.createConfirmedStudent(institutionId)
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + otroAlumno.accessToken
    When method get
    Then status 200
    And match response == '#[0]'

  Scenario: Sin sesión, 403
    Given path '/me/course-enrollments'
    When method get
    Then status 403
