Feature: Caso de uso — Prospecto se convierte en Alumno y se da de baja

  # Ciclo de vida completo de RF-AD (Gestión de personas): alta de prospecto
  # (público, sin sesión) -> documentación -> confirmación (otorga ALUMNO) ->
  # legajo visible para staff -> baja. Pedido explícito: "un alumno que se
  # anota en una carrera y se da de baja" es exactamente este flujo.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.crearInstitucion()
    * def preceptorToken = fixtures.tokenParaRol('PRECEPTOR')
    * def directorToken = fixtures.tokenParaRol('DIRECTOR')

  Scenario: Ciclo completo — prospecto, documentos, confirmar, legajo, baja
    # 1. Alta de prospecto — público, sin sesión
    * def emailProspecto = 'karate.prospecto.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/prospects'
    And request { nombre: 'QA', apellido: 'Prospecto', email: '#(emailProspecto)', telefono: '11-5555-5555', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    And match response.onboardingStatus == 'PROSPECTO'
    * def studentId = response.id

    # 2. Sin sesión no se puede agregar documentación — 403
    Given path '/prospects', studentId, 'documents'
    And param type = 'DNI'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.pdf', contentType: 'application/pdf' }
    When method post
    Then status 403

    # 3. Con sesión de Preceptor, tipo de archivo no soportado — 400
    Given path '/prospects', studentId, 'documents'
    And header Authorization = 'Bearer ' + preceptorToken
    And param type = 'DNI'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.exe', contentType: 'application/x-msdownload' }
    When method post
    Then status 400

    # 4. Documento válido — 201
    Given path '/prospects', studentId, 'documents'
    And header Authorization = 'Bearer ' + preceptorToken
    And param type = 'DNI'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201

    # 5. El mismo tipo reemplaza al anterior, no duplica — listar devuelve 1
    Given path '/prospects', studentId, 'documents'
    And header Authorization = 'Bearer ' + preceptorToken
    And param type = 'DNI'
    And multipart file file = { value: 'version 2', filename: 'dni-v2.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201

    Given path '/prospects', studentId, 'documents'
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response == '#[1]'

    # 6. Confirmar alta — 200, pasa a CONFIRMADO, otorga ALUMNO
    * def dniConfirmacion = 'KARATEPROSP' + java.lang.System.nanoTime()
    Given path '/prospects', studentId, 'confirm'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { dni: '#(dniConfirmacion)', street: 'Calle Falsa', city: 'CABA' }
    When method post
    Then status 200
    And match response.onboardingStatus == 'CONFIRMADO'

    # 7. Confirmar de nuevo — 409, ya no está PROSPECTO
    * def dniConfirmacion2 = 'KARATEPROSP2' + java.lang.System.nanoTime()
    Given path '/prospects', studentId, 'confirm'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { dni: '#(dniConfirmacion2)', street: 'Calle Falsa', city: 'CABA' }
    When method post
    Then status 409

    # 8. Legajo visible para Preceptor y para Director (matriz: Director tiene R)
    Given path '/students', studentId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response.onboardingStatus == 'CONFIRMADO'

    Given path '/students', studentId
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200

    # 9. Un Director NO puede dar de baja (matriz: solo Preceptor/Administrativo) — 403
    Given path '/students', studentId, 'withdraw'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'ABANDONO' }
    When method post
    Then status 403

    # 10. El Preceptor sí puede — 204
    Given path '/students', studentId, 'withdraw'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { reason: 'ABANDONO', detail: 'Prueba automatizada' }
    When method post
    Then status 204

    # 11. El legajo refleja la baja
    Given path '/students', studentId
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response.status == 'DELETED'

  Scenario: Alta de prospecto — campos faltantes, 400
    Given path '/prospects'
    And request { nombre: '' }
    When method post
    Then status 400
