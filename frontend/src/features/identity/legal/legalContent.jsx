import { LegalFill } from '@/features/identity/legal/LegalFill';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const THIRD_PARTIES = [
  { name: 'Mercado Pago', purpose: 'Cobrar cuotas', data: 'Nombre, correo, importe' },
  { name: 'Brevo', purpose: 'Enviar correos', data: 'Nombre, correo' },
  { name: 'Google', purpose: 'Ingresar con tu cuenta de Google', data: 'Correo, identificador' },
];

export const LEGAL_CONTENT = {
  TERMS: {
    title: 'Términos y condiciones',
    sections: [
      {
        id: 't1',
        title: '1. Quiénes somos',
        body: (
          <p>
            Este sistema es operado por <LegalFill>Razón social</LegalFill>, CUIT <LegalFill>CUIT</LegalFill>, con domicilio en{' '}
            <LegalFill>Domicilio</LegalFill> (el «Instituto»).
          </p>
        ),
      },
      {
        id: 't2',
        title: '2. Uso del sistema',
        body: (
          <p>
            Te damos acceso para consultar tu trayectoria académica, inscribirte a materias y mesas, y ver y pagar tus
            cuotas. Te comprometés a usarlo solo con tus datos y a no compartir tu contraseña.
          </p>
        ),
      },
      {
        id: 't3',
        title: '3. Cuentas y menores de edad',
        body: (
          <p>
            Si sos menor de 18 años, tu madre, padre o tutor tiene que conocer y aceptar estos términos y figurar como
            responsable en tu ficha.
          </p>
        ),
      },
      {
        id: 't4',
        title: '4. Pagos',
        body: (
          <p>
            Los pagos en línea los procesa Mercado Pago. El Instituto no almacena los datos de tu tarjeta.
          </p>
        ),
      },
      {
        id: 't5',
        title: '5. Cambios y contacto',
        body: (
          <p>
            Si cambiamos estos términos, te vamos a pedir que aceptes la nueva versión. Consultas:{' '}
            <LegalFill>Correo de contacto</LegalFill>.
          </p>
        ),
      },
    ],
  },
  PRIVACY: {
    title: 'Política de privacidad',
    sections: [
      {
        id: 'p1',
        title: '1. Responsable de los datos',
        body: (
          <p>
            <LegalFill>Razón social</LegalFill>, CUIT <LegalFill>CUIT</LegalFill>, <LegalFill>Domicilio</LegalFill>. Contacto:{' '}
            <LegalFill>Correo del responsable</LegalFill>.
          </p>
        ),
      },
      {
        id: 'p2',
        title: '2. Qué datos recogemos',
        body: (
          <p>
            Nombre, DNI, fecha de nacimiento, domicilio, correo y teléfono; datos del tutor si sos menor; asistencia,
            notas e inscripciones; cuotas y pagos.
          </p>
        ),
      },
      {
        id: 'p3',
        title: '3. Para qué los usamos y con qué base',
        body: (
          <p>
            Para gestionar tu cursada y la facturación (ejecución de la relación con el Instituto), cumplir
            obligaciones legales y avisarte novedades (tu consentimiento).
          </p>
        ),
      },
      {
        id: 'p4',
        title: '4. Con quién los compartimos',
        body: (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tercero</TableHead>
                <TableHead>Para qué</TableHead>
                <TableHead>Qué recibe</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {THIRD_PARTIES.map((party) => (
                <TableRow key={party.name}>
                  <TableCell>{party.name}</TableCell>
                  <TableCell>{party.purpose}</TableCell>
                  <TableCell>{party.data}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ),
      },
      {
        id: 'p5',
        title: '5. Cuánto tiempo los conservamos',
        body: (
          <p>
            Mientras seas alumno y después durante los plazos legales. El registro de tu aceptación de estos documentos
            se conserva como prueba.
          </p>
        ),
      },
      {
        id: 'p6',
        title: '6. Tus derechos',
        body: (
          <p>
            Podés pedir acceso, rectificación y supresión de tus datos (Ley 25.326), escribiendo a{' '}
            <LegalFill>Correo del responsable</LegalFill>. La Agencia de Acceso a la Información Pública atiende denuncias por
            incumplimientos.
          </p>
        ),
      },
      {
        id: 'p7',
        title: '7. Menores de edad',
        body: <p>Tratamos los datos de menores con la autorización de su responsable.</p>,
      },
    ],
  },
};
