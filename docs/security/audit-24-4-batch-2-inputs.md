# Auditoría 24.4, lote 2: entradas externas (H8, H17, H18)

Verificado contra `origin/develop` el 26-nov-2026. Rutas de producción relativas a `backend/src/main/java/com/red_escuela/backend/`.

## H8: subidas

| punto | estado | evidencia |
|---|---|---|
| Magic bytes en las subidas de alumnos, avatar y QR | verificado, sin cambio | `shared/storage/FileStorageService.java` (`requireSignatureOf` + `shared/storage/UploadedFileSignature.java`); la extensión sale del tipo permitido, nunca del nombre del cliente |
| `Content-Type` nulo | verificado, sin cambio | `FileStorageService.declaredContentType` devuelve `""` y `UnsupportedFileTypeException` rechaza; `attendance/specialist/TeacherAttendanceProofRecorder.java` compara por igualdad (nulo no pasa) |
| Comprobantes de transferencia | verificado, sin cambio | B6 del estado de la auditoría: `billing/payment/domain/ReceiptFileSignature.java`, `BankTransferReceiptFilePolicy` |
| Firmas e imágenes de plantilla (PNG) | verificado, sin cambio | validación por contenido en `graduation/certificate/template/specialist/TemplatePngInspector.java` y `CertificateSignatorySignatureWriter` |
| Importación CSV | verificado, sin cambio | el nombre original solo se guarda truncado; la descarga usa nombre fijo en `ContentDisposition.attachment()` |
| `Content-Disposition` | verificado, sin cambio | los nombres de descarga los genera el servidor (UUID o nombre derivado), no el del cliente |
| `nosniff` | verificado, sin cambio | `config/SecurityConfig.java:93` (`contentTypeOptions`) lo aplica a todas las respuestas |

Sin hallazgos nuevos en H8.

## H17: LIKE sin escapar

Todos los `like` del backend (Specifications, `@Query`, SQL nativo) se revisaron.

| punto | estado |
|---|---|
| `PersonSpecifications`, `StudentSpecifications`, `CommissionClosureListReader` | verificado, usan `LikePatterns.contains` con `ESCAPE` |
| `GraduationCertificateCatalog`, `GraduationCertificateReviewReader`, `GraduationCandidateFinder`, `ProgramApplicationHistory` | verificado, escapan `\`, `%` y `_` (o `!` con `escape '!'`) |
| `HeldAccountLookup` | verificado: no usa `LIKE` (consultas por id), no aplica |
| `EventPublicationAttemptLedger`, `AdmissionEnrollmentStalledNotifier` | verificado: el patrón es un nombre de clase fijo, no entrada del usuario |
| **`CertificateSignatoryReader.matching`** (`GET /certificate-signatories?q=`) | **hallazgo, corregido**: `%` y `_` del término funcionaban como comodines (`q=%` devolvía todo) |

Corrección: `LikePatterns.contains` + `LikePatterns.ESCAPE_CHARACTER`. Test: `graduation/certificate/signatory/CertificateSignatoryLikeSearchIT.java` (`50%` y `zeta_` devuelven solo el firmante literal; sin el fix devuelven ambos). Sin cambio de contrato ni migración.

## H18: replay del webhook de Mercado Pago

| punto | estado | evidencia |
|---|---|---|
| Firma HMAC y comparación en tiempo constante | verificado, sin cambio | `billing/infrastructure/MpWebhookSignatureVerifier.java`; sin secreto solo se acepta en perfil `local` |
| Ventana de tiempo | verificado, sin cambio | ±5 min sobre `ts` firmado (`requireFresh`); `ts` no numérico se rechaza |
| Idempotencia | verificado, sin cambio | modo sin acreditar: `x-request-id` único (V189, `MpWebhookRawEventRecorder`); modo FULL: lock + `isMercadoPagoPaymentRegistered` por `data.id` en `MercadoPagoWebhookRegistration` |
| Inyección en logs / `data.id` | verificado, sin cambio | `MpWebhookController` exige `^\d{1,20}$`; `LogSanitizer` |
| Pago por `external_reference` sin verificar preferencia (B4) | diferido, sin tocar | billing apagado (D-226); regla de plata |

Sin hallazgos nuevos en H18.

## Resumen

1 hallazgo corregido (H17, `CertificateSignatoryReader`), el resto verificado sin cambio. Nota de verificación: en la sesión de nube no hay Docker, por lo que el IT nuevo y `OpenApiSnapshotTest` los corre el CI; `ModularityTest`, `ArchitectureTest`, `QualityRulesTest` y `JavadocOnlyInPackageInfoTest` pasaron localmente.
