Feature: Billing configuration - collection reminder schedules by teaching group

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def administrativeToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def programId = fixtures.createProgramId()
    * def base = '/billing-configuration/collection-reminder-schedules'
    * def noticesWith =
      """
      function(beforeDueDays) {
        return [
          { noticeKey: 'issuance', messageType: 'ISSUANCE_NOTICE', milestone: 'ISSUANCE', offsetDays: 0, channels: ['IN_APP'] },
          { noticeKey: 'after-issue', messageType: 'AFTER_ISSUE_REMINDER', milestone: 'ISSUANCE', offsetDays: 5, channels: ['IN_APP', 'EMAIL'] },
          { noticeKey: 'before-due', messageType: 'BEFORE_DUE_REMINDER', milestone: 'DUE_DATE', offsetDays: beforeDueDays, channels: ['IN_APP', 'EMAIL'] },
          { noticeKey: 'after-due', messageType: 'AFTER_DUE_REMINDER', milestone: 'DUE_DATE', offsetDays: 10, channels: ['IN_APP', 'EMAIL'] }
        ];
      }
      """
    * def changeBody =
      """
      function(beforeDueDays, effectiveFrom) {
        return { schedule: { notices: noticesWith(beforeDueDays) }, effectiveFrom: effectiveFrom, changeReason: 'karate' };
      }
      """
    * def own =
      """
      function(list) {
        return karate.filter(list, function(x) { return x.targetId == programId });
      }
      """
    * def versionFrom =
      """
      function(list, date) {
        return karate.filter(list, function(v) { return v.effectiveFrom == date });
      }
      """

  Scenario: Summary lists both groups and keeps a future-dated exception out of the ruling exceptions
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request changeBody(-2, '2045-01-10')
    When method post
    Then status 201
    And match response.status == 'SCHEDULED'
    Given path base + '/PROGRAMS/exceptions/' + programId + '/removal'
    And header Authorization = 'Bearer ' + directorToken
    And request { effectiveFrom: '2045-02-10', changeReason: 'karate' }
    When method post
    Then status 201
    Given path base
    And header Authorization = 'Bearer ' + administrativeToken
    When method get
    Then status 200
    And match response.groups == '#[2]'
    And match response.groups[*].group contains only ['PROGRAMS', 'MONTHLY_COURSES']
    And match own(response.exceptions) == '#[0]'

  Scenario: A future-dated exception published by the director is not listed as ruling in the summary yet
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request changeBody(-1, '2045-01-20')
    When method post
    Then status 201
    Given path base
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match own(response.exceptions) == '#[0]'

  Scenario: Validation answers 200 with no violations for a valid list and 400 when the client asks to follow the group
    Given path base + '/PROGRAMS/validation'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-2, '2045-02-01')
    When method post
    Then status 200
    And match response.violations == []
    Given path base + '/PROGRAMS/exceptions/' + programId + '/validation'
    And header Authorization = 'Bearer ' + administrativeToken
    And request { schedule: { followsGroupSchedule: true, notices: [] }, effectiveFrom: '2045-02-01', changeReason: 'karate' }
    When method post
    Then status 400
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request { schedule: { followsGroupSchedule: true, notices: [] }, effectiveFrom: '2045-02-01', changeReason: 'karate' }
    When method post
    Then status 400
    And match response.violations[0].message contains 'removal'

  Scenario: Administrative proposes and cannot approve, the director approves and the version is scheduled
    Given path base + '/MONTHLY_COURSES/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-2, '2045-03-01')
    When method post
    Then status 202
    * def requestId = response.authorizationRequestId
    Given path base + '/MONTHLY_COURSES/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    * def proposed = versionFrom(response, '2045-03-01')
    And match proposed == '#[1]'
    And match proposed[0].status == 'PROPOSED'
    Given path '/authorization-requests/' + requestId + '/approve'
    And header Authorization = 'Bearer ' + administrativeToken
    And request {}
    When method post
    Then status 403
    Given path '/authorization-requests/' + requestId + '/approve'
    And header Authorization = 'Bearer ' + directorToken
    And request {}
    When method post
    Then status 200
    Given path base + '/MONTHLY_COURSES/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    * def approved = versionFrom(response, '2045-03-01')
    And match approved == '#[1]'
    And match approved[0].status == 'SCHEDULED'
    And match approved[0].schedule.notices[2].offsetDays == -2

  Scenario: The director publishes directly and the version is scheduled
    Given path base + '/PROGRAMS/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request changeBody(-3, '2045-04-01')
    When method post
    Then status 201
    And match response.status == 'SCHEDULED'
    And match response.directPublication == true
    And match response.effectiveFrom == '2045-04-01'
    Given path base + '/PROGRAMS/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match versionFrom(response, '2045-04-01') == '#[1]'

  Scenario: A second open proposal in the same group or exception answers 409 while another topic stays free
    Given path base + '/MONTHLY_COURSES/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-2, '2045-05-01')
    When method post
    Then status 202
    * def groupRequestId = response.authorizationRequestId
    Given path base + '/MONTHLY_COURSES/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-3, '2045-05-02')
    When method post
    Then status 409
    And match response.code == 'PROPOSAL_ALREADY_OPEN'
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-1, '2045-05-01')
    When method post
    Then status 202
    * def exceptionRequestId = response.authorizationRequestId
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-2, '2045-05-02')
    When method post
    Then status 409
    And match response.code == 'PROPOSAL_ALREADY_OPEN'
    Given path base + '/PROGRAMS/exceptions/' + programId + '/removal'
    And header Authorization = 'Bearer ' + administrativeToken
    And request { effectiveFrom: '2045-05-03', changeReason: 'karate' }
    When method post
    Then status 409
    And match response.code == 'PROPOSAL_ALREADY_OPEN'
    Given path '/authorization-requests/' + groupRequestId + '/reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'karate' }
    When method post
    Then status 200
    Given path '/authorization-requests/' + exceptionRequestId + '/reject'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'karate' }
    When method post
    Then status 200
    Given path base
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match each response.groups[*].hasOpenProposal == false
    Given path base + '/PROGRAMS/exceptions/' + programId + '/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response[*].status == ['REJECTED']
    Given path base + '/MONTHLY_COURSES/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match versionFrom(response, '2045-05-01')[0].status == 'REJECTED'

  Scenario: An exception for a program is proposed and removed through approved proposals
    Given path base + '/PROGRAMS/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + administrativeToken
    And request changeBody(-2, '2045-06-01')
    When method post
    Then status 202
    Given path '/authorization-requests/' + response.authorizationRequestId + '/approve'
    And header Authorization = 'Bearer ' + directorToken
    And request {}
    When method post
    Then status 200
    Given path base + '/PROGRAMS/exceptions/' + programId + '/removal'
    And header Authorization = 'Bearer ' + administrativeToken
    And request { effectiveFrom: '2045-07-01', changeReason: 'karate' }
    When method post
    Then status 202
    Given path '/authorization-requests/' + response.authorizationRequestId + '/approve'
    And header Authorization = 'Bearer ' + directorToken
    And request {}
    When method post
    Then status 200
    Given path base + '/PROGRAMS/exceptions/' + programId + '/versions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    * def removal = versionFrom(response, '2045-07-01')
    And match removal == '#[1]'
    And match removal[0].status == 'SCHEDULED'
    And match removal[0].schedule.followsGroupSchedule == true
    And match removal[0].directPublication == false

  Scenario: A removal dated in the past is refused
    Given path base + '/PROGRAMS/exceptions/' + programId + '/removal'
    And header Authorization = 'Bearer ' + directorToken
    And request { effectiveFrom: '2000-01-01', changeReason: 'karate' }
    When method post
    Then status 400
    And match response.code == 'RETROACTIVE_EFFECTIVE_DATE'

  Scenario Outline: A target that does not exist answers 404 on <path>
    Given path base + '<path>'
    And header Authorization = 'Bearer ' + directorToken
    And request changeBody(-2, '2045-08-01')
    When method post
    Then status 404

    Examples:
      | path                                          |
      | /PROGRAMS/exceptions/999999999/changes        |
      | /MONTHLY_COURSES/exceptions/999999999/changes |

  Scenario: A program id used as a monthly course exception answers 404
    Given path base + '/MONTHLY_COURSES/exceptions/' + programId + '/changes'
    And header Authorization = 'Bearer ' + directorToken
    And request changeBody(-2, '2045-08-01')
    When method post
    Then status 404
    Given path base + '/MONTHLY_COURSES/exceptions/' + programId + '/removal'
    And header Authorization = 'Bearer ' + directorToken
    And request { effectiveFrom: '2045-08-01', changeReason: 'karate' }
    When method post
    Then status 404

  Scenario Outline: A <role> cannot read <path>
    * def token = fixtures.tokenForRole('<role>')
    Given path '<path>'.replace('PID', '' + programId)
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 403

    Examples:
      | role | path |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/versions |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/versions |
      | DOCENTE | /billing-configuration/collection-reminder-schedules |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/versions |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/versions |
      | ALUMNO | /billing-configuration/collection-reminder-schedules |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/versions |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/versions |

  Scenario Outline: A <role> cannot write <path>
    * def token = fixtures.tokenForRole('<role>')
    Given path '<path>'.replace('PID', '' + programId)
    And header Authorization = 'Bearer ' + token
    And request changeBody(-2, '2045-09-01')
    When method post
    Then status 403

    Examples:
      | role | path |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/changes |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/validation |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/changes |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/validation |
      | PRECEPTOR | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/removal |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/changes |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/validation |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/changes |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/validation |
      | DOCENTE | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/removal |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/changes |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/validation |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/changes |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/validation |
      | ALUMNO | /billing-configuration/collection-reminder-schedules/PROGRAMS/exceptions/PID/removal |
