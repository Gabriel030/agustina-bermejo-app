@ignore
Feature: Subir un documento propio a una solicitud de alta de carrera

  Scenario: Subir un documento
    Given path '/me/program-enrollment-applications', applicationId, 'documents'
    And header Authorization = 'Bearer ' + alumnoToken
    And param type = tipo
    And multipart file file = { value: '%PDF-1.4 contenido de prueba', filename: 'documento.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
