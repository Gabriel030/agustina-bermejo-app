Feature: Catálogo — Carreras (ProgramController)

  # Cubre lo que faltaba de ProgramController además de POST (ya usado como setup en
  # todos los demás dominios): editar, desactivar/reactivar (idempotentes, sin chequeo
  # de estado previo — ver ProgramService), listar con filtro de status, 404 y 403.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')

    * def nombreCarrera = 'Karate - Carrera catálogo ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCarrera)', totalYears: 4, price: { amount: '60000', currency: 'ARS' }, totalCreditsRequired: 120 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def programId = response.id

  Scenario: Editar (PATCH-si-viene-presente), desactivar, reactivar, listar filtrado, 404
    # Editar solo un campo — el resto no cambia (comportamiento ya documentado como
    # deuda técnica en Estructura académica para UpdateProgramSubjectRequest, mismo
    # criterio acá)
    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    And request { totalYears: 5 }
    When method put
    Then status 200
    And match response.totalYears == 5
    And match response.name == nombreCarrera

    # Solo ADMINISTRATIVO puede editar — Preceptor no
    Given path '/programs', programId
    And header Authorization = 'Bearer ' + preceptorToken
    And request { totalYears: 3 }
    When method put
    Then status 403

    # Editar una carrera inexistente — 404
    Given path '/programs', 999999
    And header Authorization = 'Bearer ' + adminToken
    And request { totalYears: 3 }
    When method put
    Then status 404

    # Desactivar — 204, status pasa a INACTIVE
    Given path '/programs', programId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'INACTIVE'

    # Desactivar de nuevo es idempotente — sigue dando 204
    Given path '/programs', programId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    # Listar filtrando por INACTIVE la incluye; filtrando por ACTIVE no
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And param status = 'INACTIVE'
    When method get
    Then status 200
    * def enInactivas = response.filter(function(x){ return x.id == programId })
    And match enInactivas == '#[1]'

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And param status = 'ACTIVE'
    When method get
    Then status 200
    * def enActivas = response.filter(function(x){ return x.id == programId })
    And match enActivas == '#[0]'

    # Reactivar — 204, vuelve a ACTIVE
    Given path '/programs', programId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'ACTIVE'

    # Ver detalle de una carrera inexistente — 404
    Given path '/programs', 999999
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 404

  Scenario: Editar cuota y matrícula de una carrera creada sin precio — 200
    * def nombreSinPrecio = 'Karate - Carrera sin precio ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreSinPrecio)', totalYears: 3, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def sinPrecioId = response.id

    Given path '/programs', sinPrecioId
    And header Authorization = 'Bearer ' + adminToken
    And request { price: { amount: '45000', currency: 'ARS' }, enrollmentFeeAmount: { amount: '30000', currency: 'ARS' } }
    When method put
    Then status 200
    And match response.price == { amount: '45000.00', currency: 'ARS' }
    And match response.enrollmentFeeAmount == { amount: '30000.00', currency: 'ARS' }

  Scenario: Mandar en null un campo opcional de la carrera lo borra; omitirlo lo conserva
    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    And request { teachingMode: 'HYBRID', level: 'UNDERGRADUATE', totalHoursRequired: 900, graduationProfile: 'Perfil', admissionRequirements: 'Secundario completo' }
    When method put
    Then status 200
    And match response.graduationProfile == 'Perfil'

    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    And request { totalYears: 4 }
    When method put
    Then status 200
    And match response.teachingMode == 'HYBRID'
    And match response.level == 'UNDERGRADUATE'
    And match response.totalHoursRequired == 900
    And match response.graduationProfile == 'Perfil'
    And match response.admissionRequirements == 'Secundario completo'

    Given path '/programs', programId
    And header Authorization = 'Bearer ' + adminToken
    And request { teachingMode: null, level: null, totalHoursRequired: null, graduationProfile: null, admissionRequirements: null }
    When method put
    Then status 200
    And match response.teachingMode == null
    And match response.level == null
    And match response.totalHoursRequired == null
    And match response.graduationProfile == null
    And match response.admissionRequirements == null

  Scenario: El catálogo público solo lista las carreras publicadas, ordenadas por nombre
    * def alumnoToken = fixtures.tokenForRole('ALUMNO')
    * def sufijo = '' + java.lang.System.nanoTime()

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate catálogo Z " + sufijo)', totalYears: 3, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def lastId = response.id

    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#("Karate catálogo A " + sufijo)', totalYears: 3, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def firstId = response.id

    Given path '/programs', lastId, 'catalog-visibility'
    And header Authorization = 'Bearer ' + adminToken
    And request { visible: true }
    When method put
    Then status 200

    Given path '/programs', firstId, 'catalog-visibility'
    And header Authorization = 'Bearer ' + adminToken
    And request { visible: true }
    When method put
    Then status 200

    Given path '/programs'
    And header Authorization = 'Bearer ' + alumnoToken
    And param status = 'ACTIVE'
    And param visibleInCatalog = true
    When method get
    Then status 200
    * def ids = karate.map(response, function(x){ return x.id })
    And match ids contains firstId
    And match ids contains lastId
    And match ids !contains programId
    And assert ids.indexOf(firstId) < ids.indexOf(lastId)

  Scenario: Las cohortes del catálogo informan si su ventana de admisión está abierta
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#("Karate - Periodo cohortes " + java.lang.System.nanoTime())', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    Given path '/programs', programId, 'year-billing-templates'
    And header Authorization = 'Bearer ' + directorToken
    And request { termId: '#(termId)', recurringFeeInvoicesCount: 10 }
    When method post
    Then status 201
    * def templateId = response.id

    Given path '/year-billing-templates', templateId, 'cohorts'
    And header Authorization = 'Bearer ' + directorToken
    And request { intake: 'FIRST', admissionStart: '2020-01-01', admissionEnd: '2020-03-31', cursadaStart: '2020-04-01' }
    When method post
    Then status 201
    * def closedCohortId = response.id

    Given path '/year-billing-templates', templateId, 'cohorts'
    And header Authorization = 'Bearer ' + directorToken
    And request { intake: 'SECOND', admissionStart: '2020-01-01', admissionEnd: '2099-12-31', cursadaStart: '2100-03-01' }
    When method post
    Then status 201
    * def openCohortId = response.id

    Given path '/programs', programId, 'cohorts'
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    * def closedCohort = karate.filter(response, function(x){ return x.id == closedCohortId })[0]
    * def openCohort = karate.filter(response, function(x){ return x.id == openCohortId })[0]
    And match closedCohort.admissionOpen == false
    And match closedCohort.admissionEnd == '2020-03-31'
    And match openCohort.admissionOpen == true
