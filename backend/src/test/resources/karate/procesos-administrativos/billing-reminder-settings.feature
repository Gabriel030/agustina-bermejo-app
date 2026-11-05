Feature: Billing configuration - global collection reminder settings

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def administrativeToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def base = '/billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS'
    * def settingsBody =
      """
      function(dailyCap, effectiveFrom) {
        return {
          values: {
            suspensionNoticeEnabled: true,
            postWithdrawalSummaryMonths: 1,
            emailWindowStart: '09:00',
            emailWindowEnd: '11:00',
            dailyEmailCap: dailyCap,
            contactListDaysOverdue: 14,
            contactListOverdueInstallments: 2
          },
          effectiveFrom: effectiveFrom,
          changeReason: 'karate'
        };
      }
      """
    * def versionFrom =
      """
      function(list, date) {
        return karate.filter(list, function(v) { return v.effectiveFrom == date });
      }
      """

  Scenario: The director publishes settings with a daily cap of 280 and they are scheduled
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request settingsBody(280, '2046-01-10')
    When method post
    Then status 201
    And match response.status == 'SCHEDULED'
    And match response.values.dailyEmailCap == 280
    Given path base + '/versions'
    And header Authorization = 'Bearer ' + administrativeToken
    When method get
    Then status 200
    * def published = versionFrom(response, '2046-01-10')
    And match published == '#[1]'
    And match published[0].values.dailyEmailCap == 280

  Scenario: Administrative proposes settings with a cap of 280 and the director approves them
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request settingsBody(280, '2046-02-10')
    When method post
    Then status 202
    * def requestId = response.authorizationRequestId
    Given path base + '/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match versionFrom(response, '2046-02-10')[0].status == 'PROPOSED'
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request settingsBody(280, '2046-02-11')
    When method post
    Then status 409
    And match response.code == 'PROPOSAL_ALREADY_OPEN'
    Given path '/authorization-requests/' + requestId + '/approve'
    And header Authorization = 'Bearer ' + directorToken
    And request {}
    When method post
    Then status 200
    Given path base + '/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match versionFrom(response, '2046-02-10')[0].status == 'SCHEDULED'

  Scenario: A proposal rejected by the director leaves the topic free
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request settingsBody(280, '2046-03-10')
    When method post
    Then status 202
    Given path '/authorization-requests/' + response.authorizationRequestId + '/reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'karate' }
    When method post
    Then status 200
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request settingsBody(280, '2046-03-11')
    When method post
    Then status 202
    Given path '/authorization-requests/' + response.authorizationRequestId + '/reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'karate' }
    When method post
    Then status 200

  Scenario: The legacy field daysBeforeDueReminder is ignored by the settings payload
    * def payload = settingsBody(280, '2046-04-10')
    * set payload.values.daysBeforeDueReminder = 3
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request payload
    When method post
    Then status 201
    And match response.values contains { dailyEmailCap: 280 }
    And match response.values !contains { daysBeforeDueReminder: '#notnull' }

  Scenario: Validation answers 200 with no violations and 200 with violations for a bad window
    Given path base + '/validation'
    And header Authorization = 'Bearer ' + administrativeToken
    And request settingsBody(280, '2046-05-10')
    When method post
    Then status 200
    And match response.violations == []
    * def broken = settingsBody(280, '2046-05-10')
    * set broken.values.emailWindowStart = '11:00'
    * set broken.values.emailWindowEnd = '09:00'
    Given path base + '/validation'
    And header Authorization = 'Bearer ' + administrativeToken
    And request broken
    When method post
    Then status 200
    And match response.violations == '#[1]'

  Scenario: Settings dated in the past are refused
    Given path base + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request settingsBody(280, '2000-01-01')
    When method post
    Then status 400
    And match response.code == 'RETROACTIVE_EFFECTIVE_DATE'

  Scenario Outline: A <role> cannot write settings on <path>
    * def token = fixtures.tokenForRole('<role>')
    Given path base + '<path>'
    And header Authorization = 'Bearer ' + token
    And request settingsBody(280, '2046-06-10')
    When method post
    Then status 403

    Examples:
      | role      | path        |
      | PRECEPTOR | /changes    |
      | PRECEPTOR | /validation |
      | DOCENTE   | /changes    |
      | DOCENTE   | /validation |
      | ALUMNO    | /changes    |
      | ALUMNO    | /validation |

  Scenario Outline: A <role> cannot read the settings versions
    * def token = fixtures.tokenForRole('<role>')
    Given path base + '/versions'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 403

    Examples:
      | role      |
      | PRECEPTOR |
      | DOCENTE   |
      | ALUMNO    |
