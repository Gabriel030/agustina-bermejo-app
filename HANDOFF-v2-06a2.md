# HANDOFF V2-06a2 (agente frenado por presupuesto, ~360k)

Hecho (commits locales en refactor/fase-v2-06a2-configuration-cutover):
- Main completo: lectores de YEAR_TERMS, COHORT_ENTRY, LATE_PAYMENT, COURSE_TERMS, IRREGULAR_TERMS pasan por BillingConfigurationReader a la fecha del hecho; V99 borra columnas viejas y late_payment_settings; PATCH/POST legacy son alias del circuito; LegacyConfigurationMirror reemplazado por DirectConfigurationPublisher.
- D-225: la retirada ya existia (AuthorizationRequestDecider.withdraw + ConfigurationProposalObserver.afterWithdrawn); se agregaron 4 tests @Tag("D-225") en VersionedConfigurationIntegrationTest; registro en implementada.
- Escenario temporal: BillingConfigurationPoliciesIntegrationTest#aScheduledDueDaysVersionRulesOnlyFromItsStartDate.

Pendiente (ultimo clean verify -P mutation: 2032 tests, 8 fallas, 44 errores):
1. BillingInvariantChecker I-15 (arreglado en el ultimo commit, revalidar): 33+ errores BadSqlGrammar.
2. BillingDecisionCoverageTest: D-212 y D-213 quedaron sin test con tag al borrar LegacyConfigurationMirrorIntegrationTest. Agregar tests @Tag("D-212") (alias PATCH cohorte -> version COHORT_ENTRY, lectura por fecha) y @Tag("D-213") (IRREGULAR_TERMS versionado afecta ventanas de matricula).
3. ModularityTest: enrollment.GrantEnrollmentFeeExemptionService importa billing.configuration.* internos; exponer en billing/api (interfaz nombrada) o usar un puerto existente.
4. Tests de cohorte con descuento por defecto 50% (ProgramCohortBillingTest:260,308; InvoiceIssuingServiceTest t02/t03): el fixture persiste cohortes sin version COHORT_ENTRY; publicar via TestTemplateTerms.publishCohortEntryDiscount tras persistir.
5. InvoiceOverdueSweepServiceTest:170 y EnrollmentBillingComplianceSweepServiceTest:245: revisar fechas de la version LATE_PAYMENT de fixture (effectiveFrom now-1y) vs MutableClock.
6. ProgramEnrollmentApplicationUseCaseTest:269: TestTemplateTerms.latest usa fecha 2100; la plantilla tiene version con effectiveFrom futuro?; revisar.
7. Correr de nuevo: ./mvnw clean verify -P mutation (con semaforo), anotar fila en docs/testing/local-test-log.md, luego un solo push y PR contra develop.
