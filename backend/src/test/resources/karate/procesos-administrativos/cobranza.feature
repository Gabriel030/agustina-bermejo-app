Feature: Cobranza — política de mora y webhook de Mercado Pago

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

  Scenario Outline: Una política con recargo fuera de 0 a 100 se rechaza — 400
    Given path '/late-payment-settings'
    And header Authorization = 'Bearer ' + directorToken
    And request { gracePeriodDays: 10, surchargePercent: <recargo>, suspensionAfterDays: 30, suspensionAfterInvoiceCount: 2 }
    When method post
    Then status 400

    Examples:
      | recargo |
      | 150     |
      | -10     |

  Scenario: Sin secreto configurado el webhook de Mercado Pago no se procesa — 401
    Given path '/payments/mp-webhook'
    And param data.id = '123456'
    And request {}
    When method post
    Then status 401
