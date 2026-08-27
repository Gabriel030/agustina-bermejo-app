Feature: Caso de uso — Inscribirse a un curso

  # Este es el flujo donde apareció el bug real de Épica 7 Fase 2 (rol ALUMNO no
  # otorgado — ver CourseEnrollmentService.matricularComoAutoservicio y su
  # comentario). El escenario "otorga rol ALUMNO" de acá es el que lo hubiera
  # atrapado si hubiera existido antes de mergear esa fase.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.crearInstitucion()
    * def adminToken = fixtures.tokenParaRol('ADMINISTRATIVO')

    # Periodo + curso de prueba (staff)
    * def descripcionPeriodo = 'Karate - Periodo ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodo)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombreCurso = 'Karate - Curso ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', schedule: 'Sáb 10-13hs', capacity: 20, price: 15000, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    # Alumno recién registrado
    * def emailAlumno = 'karate.inscribirmecurso.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'InscribeCurso', email: '#(emailAlumno)', username: '#(emailAlumno)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    * def alumnoToken = response.accessToken

  Scenario: Inscripción con cupo otorga ALUMNO sin aprobación, no permite duplicado, 404 en curso inexistente
    # Happy path — 201
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { courseId: '#(courseId)', sectionOverrides: {} }
    When method post
    Then status 201

    # El request que hubiera atrapado el bug real: ALUMNO otorgado en la misma
    # operación, sin transacción de test de por medio que lo enmascare.
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles contains 'ALUMNO'

    # Inscribirme de nuevo al mismo curso — 409
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { courseId: '#(courseId)', sectionOverrides: {} }
    When method post
    Then status 409

    # Inscribirme a un curso inexistente — 404
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { courseId: 999999, sectionOverrides: {} }
    When method post
    Then status 404
