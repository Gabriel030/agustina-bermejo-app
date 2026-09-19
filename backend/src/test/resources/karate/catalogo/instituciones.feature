Feature: Catálogo — Instituciones

  # InstitutionController solo expone GET (el alta es interna, ver KarateFixtures.createInstitution
  # que la mayoría de los demás features usan como setup) — acá se cubre ese único endpoint real.

  Background:
    * url baseUrl

  Scenario: Listar instituciones — requiere autenticación, incluye las creadas por fixture
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.createInstitution()
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    # Sin token — 200: es catálogo público (SecurityConfig, permitAll explícito para
    # GET /institutions), a propósito distinto del resto de la API que exige JWT —
    # sostiene el flujo de Épica 7 de "catálogo visible antes de registrarse".
    Given path '/institutions'
    When method get
    Then status 200

    Given path '/institutions'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def creada = response.filter(function(x){ return x.id == institutionId })[0]
    And match creada != null
