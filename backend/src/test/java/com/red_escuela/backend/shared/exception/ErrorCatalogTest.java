package com.red_escuela.backend.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCatalogTest {

    private static final Path CATALOG = Path.of("../docs/api/error-catalog.md");
    private static final String BASE_PACKAGE = "com.red_escuela.backend";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private record TransversalError(String code, int status, boolean retryable, String origin, String reason) {
    }

    private record DomainError(String code, int status, boolean retryable, String exceptionName, String module) {
    }

    private record WriteEndpoint(String httpMethod, String path, String handler, String idempotency) {
    }

    private static final Map<Class<?>, TransversalError> TRANSVERSAL_HANDLERS = transversalHandlers();

    private static Map<Class<?>, TransversalError> transversalHandlers() {
        Map<Class<?>, TransversalError> handlers = new LinkedHashMap<>();
        handlers.put(org.springframework.web.bind.MethodArgumentNotValidException.class, new TransversalError(
                ErrorCodeResponse.VALIDATION_FAILED_CODE, 400, false, "Bean Validation del cuerpo",
                "El pedido es inválido; repetirlo igual falla igual"));
        handlers.put(org.springframework.http.converter.HttpMessageNotReadableException.class, new TransversalError(
                ErrorCodeResponse.MALFORMED_REQUEST_BODY_CODE, 400, false, "JSON ilegible o con tipos incorrectos",
                "El pedido es inválido; repetirlo igual falla igual"));
        handlers.put(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class, new TransversalError(
                ErrorCodeResponse.INVALID_PARAMETER_TYPE_CODE, 400, false, "Parámetro de ruta o query con tipo incorrecto",
                "El pedido es inválido; repetirlo igual falla igual"));
        handlers.put(org.springframework.dao.DataIntegrityViolationException.class, new TransversalError(
                ErrorCodeResponse.DATA_INTEGRITY_VIOLATION_CODE, 409, false, "Restricción de base de datos violada",
                "Choca con el estado persistido; hay que releer y decidir"));
        handlers.put(org.springframework.orm.ObjectOptimisticLockingFailureException.class, new TransversalError(
                ErrorCodeResponse.CONCURRENT_MODIFICATION_CODE, 409, false, "Otro usuario modificó el mismo registro",
                "Hay que releer el registro antes de reintentar; repetir a ciegas pisa cambios ajenos"));
        handlers.put(org.springframework.dao.PessimisticLockingFailureException.class, new TransversalError(
                ErrorCodeResponse.LOCK_CONFLICT_CODE, 409, true, "Deadlock, espera de bloqueo vencida o fallo de serialización en la base",
                "Otra operación tenía tomados los mismos registros; repetir el mismo pedido suele funcionar"));
        handlers.put(org.springframework.dao.QueryTimeoutException.class, new TransversalError(
                ErrorCodeResponse.DATABASE_BUSY_CODE, 503, true, "Consulta cancelada por superar el tiempo máximo de la base",
                "La base está ocupada; repetir tras el Retry-After puede funcionar"));
        handlers.put(IllegalArgumentException.class, new TransversalError(
                ErrorCodeResponse.codeOf(IllegalArgumentException.class), 400, false, "Argumento ilegal no previsto por el dominio",
                "El pedido es inválido; repetirlo igual falla igual"));
        handlers.put(IllegalStateException.class, new TransversalError(
                ErrorCodeResponse.codeOf(IllegalStateException.class), 409, false, "Estado ilegal no previsto por el dominio",
                "Choca con el estado persistido; hay que releer y decidir"));
        handlers.put(org.springframework.web.multipart.MaxUploadSizeExceededException.class, new TransversalError(
                ErrorCodeResponse.codeOf(org.springframework.web.multipart.MaxUploadSizeExceededException.class), 400, false, "Archivo mayor al límite",
                "El archivo es demasiado grande; repetirlo igual falla igual"));
        handlers.put(Exception.class, new TransversalError(
                ErrorCodeResponse.INTERNAL_ERROR_CODE, 500, false, "Error inesperado o integridad de datos rota",
                "Es un defecto del servidor y repetir el mismo pedido lo repite; el detalle queda solo en el log"));
        return handlers;
    }

    private static List<TransversalError> securityErrors() {
        return List.of(
                new TransversalError(ErrorCodeResponse.UNAUTHENTICATED_CODE, 401, false, "Falta el token (filtro de seguridad)",
                        "Hay que autenticarse; el cliente decide si renueva sesión"),
                new TransversalError(ErrorCodeResponse.TOKEN_EXPIRED_CODE, 401, false, "Token vencido o inválido (filtro de seguridad)",
                        "Hay que renovar el token con el refresh; el mismo pedido sin renovar falla igual"),
                new TransversalError(ErrorCodeResponse.FORBIDDEN_CODE, 403, false, "Rol insuficiente (filtro de seguridad)",
                        "Los permisos no cambian por esperar"));
    }

    @Test
    void errorCatalogDocumentMatchesTheCodeThatTheBackendCanEmit() throws IOException {
        String declared = render();

        if (Boolean.getBoolean("error-catalog.write")) {
            Files.createDirectories(CATALOG.getParent());
            Files.writeString(CATALOG, declared);
        }

        assertThat(CATALOG).as("run ./mvnw test -Dtest=ErrorCatalogTest -Derror-catalog.write=true and commit the result").exists();
        assertThat(Files.readString(CATALOG))
                .as("docs/api/error-catalog.md is out of date: run ./mvnw test -Dtest=ErrorCatalogTest -Derror-catalog.write=true and commit")
                .isEqualTo(declared);
    }

    @Test
    void everyExceptionHandlerOfTheAdviceIsEitherADomainFamilyOrACatalogedTransversalError() {
        Set<Class<?>> uncatalogued = uncataloguedHandlerTypes(GlobalExceptionHandler.class);

        assertThat(uncatalogued).as("tipos de excepción con handler y sin entrada en el catálogo").isEmpty();
    }

    @Test
    void aHandlerForAnUncataloguedExceptionTypeIsReported() {
        assertThat(uncataloguedHandlerTypes(AdviceWithUncataloguedHandler.class)).containsExactly(IOException.class);
    }

    @Test
    void everyDomainExceptionHasAStatusACodeAndARetryableFlagInTheCatalog() {
        List<DomainError> errors = domainErrors();

        assertThat(errors).hasSizeGreaterThan(300);
        assertThat(errors).allSatisfy(error -> {
            assertThat(error.code()).matches("[A-Z][A-Z0-9_]+");
            assertThat(error.status()).isBetween(400, 599);
        });
        assertThat(errors).filteredOn(DomainError::retryable)
                .allSatisfy(error -> assertThat(error.status()).isIn(429, 502, 503, 504));
        assertThat(errors).filteredOn(error -> error.status() == 429 || error.status() == 502)
                .allSatisfy(error -> assertThat(error.retryable()).isTrue());
        assertThat(errors).filteredOn(error -> error.status() == 409 || error.status() / 100 == 4 && error.status() != 429)
                .allSatisfy(error -> assertThat(error.retryable()).isFalse());
    }

    @Test
    void everyServerErrorThatLeaksNoInternalDetailsUsesTheGenericCode() {
        assertThat(domainErrors()).filteredOn(error -> error.status() == 500)
                .allSatisfy(error -> assertThat(error.code()).isEqualTo(ErrorCodeResponse.INTERNAL_ERROR_CODE));
    }

    @Test
    void theCatalogedWriteEndpointsIncludeEveryEndpointThatReadsTheIdempotencyKey() {
        List<WriteEndpoint> endpoints = writeEndpoints();

        assertThat(endpoints).filteredOn(endpoint -> !endpoint.idempotency().equals("no")).hasSizeGreaterThanOrEqualTo(8);
        assertThat(endpoints).filteredOn(endpoint -> endpoint.idempotency().equals("obligatoria"))
                .extracting(WriteEndpoint::handler).contains("AnnualUpfrontRequestController#requestAnnualUpfront");
    }

    private static class AdviceWithUncataloguedHandler {
        @ExceptionHandler(IOException.class)
        @ResponseStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
        public void handleIo() {
        }

        @ExceptionHandler(BadRequestException.class)
        @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
        public void handleBadRequest() {
        }
    }

    private static Set<Class<?>> uncataloguedHandlerTypes(Class<?> advice) {
        Set<Class<?>> uncatalogued = new TreeSet<>(Comparator.comparing(Class::getName));
        for (Method method : advice.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler == null) {
                continue;
            }
            for (Class<?> type : handler.value()) {
                boolean transversal = TRANSVERSAL_HANDLERS.containsKey(type);
                boolean domain = domainThrowables().stream().anyMatch(type::isAssignableFrom);
                if (!transversal && !domain) {
                    uncatalogued.add(type);
                }
            }
        }
        return uncatalogued;
    }

    private static List<Class<?>> domainThrowablesCache;
    private static List<WriteEndpoint> writeEndpointsCache;

    private static synchronized List<Class<?>> domainThrowables() {
        if (domainThrowablesCache == null) {
            domainThrowablesCache = scanDomainThrowables();
        }
        return domainThrowablesCache;
    }

    private static synchronized List<WriteEndpoint> writeEndpoints() {
        if (writeEndpointsCache == null) {
            writeEndpointsCache = scanWriteEndpoints();
        }
        return writeEndpointsCache;
    }

    private static List<Class<?>> scanDomainThrowables() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));
        List<Class<?>> types = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = loadClass(definition.getBeanClassName());
            if (isMainClass(type) && !Modifier.isAbstract(type.getModifiers())) {
                types.add(type);
            }
        }
        return types;
    }

    private static boolean isMainClass(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith("/classes/");
    }

    private static Class<?> loadClass(String name) {
        try {
            return ClassUtils.forName(name, ErrorCatalogTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError failure) {
            throw new IllegalStateException(name, failure);
        }
    }

    private static List<DomainError> domainErrors() {
        List<DomainError> errors = new ArrayList<>();
        for (Class<?> type : domainThrowables()) {
            Class<?> handled = mostSpecificHandledType(type);
            int status = statusOf(handled);
            boolean fixedGenericCode = handled == Exception.class || DataIntegrityBrokenException.class.isAssignableFrom(type);
            String code = fixedGenericCode ? ErrorCodeResponse.INTERNAL_ERROR_CODE : ErrorCodeResponse.codeOf(type);
            boolean retryable = !fixedGenericCode && ErrorCodeResponse.isRetryable(type);
            errors.add(new DomainError(code, status, retryable, type.getSimpleName(), moduleOf(type)));
        }
        errors.sort(Comparator.comparingInt(DomainError::status).thenComparing(DomainError::code).thenComparing(DomainError::exceptionName));
        return errors;
    }

    private static Class<?> mostSpecificHandledType(Class<?> type) {
        Class<?> best = null;
        for (Method method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler == null) {
                continue;
            }
            for (Class<?> candidate : handler.value()) {
                if (candidate.isAssignableFrom(type) && (best == null || best.isAssignableFrom(candidate))) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static int statusOf(Class<?> handledType) {
        for (Method method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
            if (handler != null && Arrays.asList(handler.value()).contains(handledType)) {
                return method.getAnnotation(ResponseStatus.class).value().value();
            }
        }
        throw new IllegalStateException(handledType.getName());
    }

    private static String moduleOf(Class<?> type) {
        String relative = type.getPackageName().substring(BASE_PACKAGE.length() + 1);
        int dot = relative.indexOf('.');
        return dot < 0 ? relative : relative.substring(0, dot);
    }

    private static List<WriteEndpoint> scanWriteEndpoints() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<WriteEndpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = loadClass(definition.getBeanClassName());
            if (!isMainClass(controller)) {
                continue;
            }
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null || method.isSynthetic()) {
                    continue;
                }
                for (RequestMethod requestMethod : mapping.method()) {
                    if (requestMethod == RequestMethod.POST || requestMethod == RequestMethod.PATCH) {
                        String suffix = mapping.path().length == 0 ? "" : mapping.path()[0];
                        endpoints.add(new WriteEndpoint(requestMethod.name(), prefix + suffix,
                                controller.getSimpleName() + "#" + method.getName(), idempotencyOf(method)));
                    }
                }
            }
        }
        endpoints.sort(Comparator.comparing(WriteEndpoint::path).thenComparing(WriteEndpoint::httpMethod).thenComparing(WriteEndpoint::handler));
        return endpoints;
    }

    private static String idempotencyOf(Method method) {
        for (Parameter parameter : method.getParameters()) {
            RequestHeader header = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
            if (header != null && IDEMPOTENCY_HEADER.equalsIgnoreCase(header.name())) {
                return header.required() ? "obligatoria" : "opcional";
            }
        }
        return "no";
    }

    private static String render() {
        StringBuilder out = new StringBuilder();
        out.append("# Catálogo de errores y política de reintentos\n\n");
        out.append("> Generado por `ErrorCatalogTest`. No se edita a mano: `./mvnw test -Dtest=ErrorCatalogTest -Derror-catalog.write=true`.\n\n");
        out.append("## Cuerpo estándar\n\n");
        out.append("Todo error de la API sale con `{\"code\": \"...\", \"retryable\": true|false, \"requestId\": \"...\", \"violations\": [...], \"details\": {...}}`. ");
        out.append("`code` es estable (derivado del nombre de la excepción o fijo para los transversales), `retryable` indica si repetir el mismo pedido puede tener éxito, ");
        out.append("`requestId` (opcional) correlaciona con el log y `violations` (opcional) trae las reglas de negocio violadas y `details` (opcional, aditivo) trae datos estables del error: `PENDING_DEBT` informa `totalDue` (monto adeudado) e `installmentCount` (cuotas pendientes). ");
        out.append("Un `429` o `503` incluye además el header `Retry-After` en segundos. Los mensajes de las excepciones son internos y no se envían: el front traduce por `code`. Un `404` de una excepción que no declara código sale sin cuerpo a propósito (anti-enumeración: un recurso ajeno es indistinguible de uno inexistente).\n\n");
        out.append("## Política de reintentos\n\n");
        out.append("| HTTP | Reintentable | Por qué |\n|---|---|---|\n");
        out.append("| 400 | no | El pedido es inválido; repetirlo igual falla igual |\n");
        out.append("| 401 | no | Hay que autenticarse o renovar el token antes de repetir |\n");
        out.append("| 403 | no | Los permisos no cambian por esperar |\n");
        out.append("| 404 | no | El recurso no existe |\n");
        out.append("| 409 | no | Conflicto con el estado persistido; hay que releer y decidir |\n");
        out.append("| 429 | sí | Límite de tasa; esperar `Retry-After` |\n");
        out.append("| 500 | no | Defecto del servidor; repetirlo repite el defecto |\n");
        out.append("| 502 | sí | Falla de un servicio externo (por ejemplo Mercado Pago), transitoria |\n");
        out.append("| 503 | sí | Servicio externo protegido por circuit breaker abierto (`EXTERNAL_SERVICE_UNAVAILABLE`); esperar `Retry-After` |\n");
        out.append("| 504 | sí | Reservado: hoy el backend no lo emite; si lo emite un proxy, se reintenta |\n\n");
        out.append("Regla del front (decisión del 3-nov-2026): GET, PUT y DELETE reintentables; POST solo con `Idempotency-Key`; máximo 3 intentos con espera exponencial y jitter, solo ante errores de red o 502, 503 y 504.\n\n");
        out.append("## Errores transversales\n\n");
        out.append("| Código | HTTP | Reintentable | Origen | Por qué |\n|---|---|---|---|---|\n");
        List<TransversalError> transversal = new ArrayList<>(securityErrors());
        transversal.addAll(TRANSVERSAL_HANDLERS.values());
        transversal.sort(Comparator.comparingInt(TransversalError::status).thenComparing(TransversalError::code));
        for (TransversalError error : transversal) {
            out.append("| `").append(error.code()).append("` | ").append(error.status()).append(" | ").append(error.retryable() ? "sí" : "no")
                    .append(" | ").append(error.origin()).append(" | ").append(error.reason()).append(" |\n");
        }
        out.append("\n## Errores de dominio\n\n");
        out.append("Cada excepción concreta del backend. El código sale del nombre de la clase; las que caen en el handler genérico (500) se informan como `INTERNAL_ERROR` y su detalle queda solo en el log.\n\n");
        List<DomainError> domain = domainErrors();
        int[] statuses = domain.stream().mapToInt(DomainError::status).distinct().sorted().toArray();
        for (int status : statuses) {
            out.append("### HTTP ").append(status).append("\n\n");
            out.append("| Código | Reintentable | Excepción | Módulo |\n|---|---|---|---|\n");
            for (DomainError error : domain) {
                if (error.status() == status) {
                    out.append("| `").append(error.code()).append("` | ").append(error.retryable() ? "sí" : "no").append(" | `")
                            .append(error.exceptionName()).append("` | ").append(error.module()).append(" |\n");
                }
            }
            out.append("\n");
        }
        out.append("## Idempotencia de las escrituras\n\n");
        out.append("Se asume idempotentes por contrato HTTP los PUT y DELETE. Los POST y PATCH de abajo se clasifican por su manejo de `Idempotency-Key`: ");
        out.append("`obligatoria` (el backend la exige), `opcional` (la acepta; sin clave un reintento puede duplicar el efecto) o `no` (no la lee; un reintento puede duplicar el efecto).\n\n");
        List<WriteEndpoint> endpoints = writeEndpoints();
        for (String kind : List.of("obligatoria", "opcional", "no")) {
            out.append("### Idempotency-Key ").append(kind).append("\n\n");
            out.append("| Método | Ruta | Handler |\n|---|---|---|\n");
            for (WriteEndpoint endpoint : endpoints) {
                if (endpoint.idempotency().equals(kind)) {
                    out.append("| ").append(endpoint.httpMethod()).append(" | `").append(endpoint.path()).append("` | `")
                            .append(endpoint.handler()).append("` |\n");
                }
            }
            out.append("\n");
        }
        return out.toString();
    }
}
