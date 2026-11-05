export const DOCUMENT_ACCEPT = 'application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png';

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
const MAX_BYTES = 5 * 1024 * 1024;

const TYPE_MESSAGE = 'El archivo tiene que ser un PDF, JPG o PNG.';

const MESSAGE_BY_CODE = {
  UNSUPPORTED_FILE_TYPE: TYPE_MESSAGE,
  APPLICATION_DOCUMENTS_LOCKED: 'Esta solicitud ya no admite cambios de documentos. Recargá la página para ver su estado.',
  MAX_UPLOAD_SIZE_EXCEEDED: 'El archivo pesa más de 5 MB. Subí uno más liviano.',
};

const NETWORK_MESSAGE = 'No pudimos subir el archivo por un problema de conexión. Probá de nuevo.';
const GENERIC_MESSAGE = 'No pudimos subir el archivo. Probá de nuevo.';

function megabytes(bytes) {
  return (bytes / (1024 * 1024)).toLocaleString('es-AR', { maximumFractionDigits: 1 });
}

export function validateDocumentFile(file) {
  if (!ALLOWED_TYPES.includes(file.type)) return TYPE_MESSAGE;
  if (file.size > MAX_BYTES) return `El archivo pesa ${megabytes(file.size)} MB; el máximo es 5 MB.`;
  return null;
}

export function documentUploadErrorMessage(error) {
  if (error?.code && MESSAGE_BY_CODE[error.code]) return MESSAGE_BY_CODE[error.code];
  if (error?.name === 'NetworkError') return NETWORK_MESSAGE;
  if (error.status === 409) return 'Esta solicitud ya no admite cambios de documentos.';
  return GENERIC_MESSAGE;
}
