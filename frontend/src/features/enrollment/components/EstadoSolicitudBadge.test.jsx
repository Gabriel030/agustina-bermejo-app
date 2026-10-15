import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EstadoSolicitudBadge } from '@/features/enrollment/components/EstadoSolicitudBadge';

const factura = (extra) => ({ id: 77, paymentStatus: 'PENDING', ...extra });

describe('EstadoSolicitudBadge', () => {
  it.each([
    ['PENDING', undefined, 'En revisión'],
    ['PENDING_REVIEW', undefined, 'En revisión'],
    ['REJECTED', undefined, 'Rechazada'],
    ['APPROVED', undefined, 'Aprobada'],
    ['APPROVED', null, 'Aprobada'],
    ['WAITLISTED', undefined, 'En lista de espera'],
  ])('muestra %s sin factura como %s', (status, admissionInvoice, etiqueta) => {
    render(<EstadoSolicitudBadge solicitud={{ status, admissionInvoice }} />);
    expect(screen.getByText(etiqueta)).toBeInTheDocument();
  });

  it('una solicitud aprobada con la matrícula impaga pide pagarla', () => {
    render(<EstadoSolicitudBadge solicitud={{ status: 'APPROVED', admissionInvoice: factura() }} />);
    expect(screen.getByText('Pagá la matrícula')).toBeInTheDocument();
  });

  it('una solicitud aprobada con la matrícula vencida lo dice en tono de peligro', () => {
    render(<EstadoSolicitudBadge solicitud={{ status: 'APPROVED', admissionInvoice: factura({ paymentStatus: 'OVERDUE' }) }} />);
    const pill = screen.getByText('Matrícula vencida');
    expect(pill.className).toContain('text-danger');
  });

  it('una solicitud aprobada con la matrícula pagada y la inscripción creada figura como inscripto', () => {
    render(<EstadoSolicitudBadge solicitud={{ status: 'APPROVED', admissionInvoice: factura({ paymentStatus: 'PAID' }), programEnrollmentId: 15 }} />);
    expect(screen.getByText('Inscripto').className).toContain('text-success');
  });

  it('una solicitud aprobada con la matrícula pagada pero sin inscripción figura como confirmando inscripción', () => {
    render(<EstadoSolicitudBadge solicitud={{ status: 'APPROVED', admissionInvoice: factura({ paymentStatus: 'PAID' }), programEnrollmentId: null }} />);
    expect(screen.getByText('Confirmando inscripción').className).toContain('text-warning');
    expect(screen.queryByText('Inscripto')).not.toBeInTheDocument();
  });

  it('una matrícula con comprobante en revisión figura como pago en revisión', () => {
    render(<EstadoSolicitudBadge solicitud={{ status: 'APPROVED', admissionInvoice: factura({ receiptStatus: 'PENDING_REVIEW' }) }} />);
    expect(screen.getByText('Pago en revisión')).toBeInTheDocument();
  });
});
