Feature: Registrar pago presencial (POST /payments)

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def alumnoToken = fixtures.tokenForRole('ALUMNO')

  Scenario: Alumno cannot register a payment on its own — 403
    Given path '/payments'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { allocations: [{ invoiceId: 999999, amountApplied: { amount: '1.00', currency: 'ARS' } }], method: 'CASH' }
    When method post
    Then status 403
