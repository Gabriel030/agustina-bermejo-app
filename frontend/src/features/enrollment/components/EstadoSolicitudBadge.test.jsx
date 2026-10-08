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
    render(<EstadoSolicitudBadge status={status} admissionInvoice={admissionInvoice} />);
    expect(screen.getByText(etiqueta)).toBeInTheDocument();
  });

  it('una solicitud aprobada con la matrícula impaga pide pagarla', () => {
    render(<EstadoSolicitudBadge status="APPROVED" admissionInvoice={factura()} />);
    expect(screen.getByText('Pagá la matrícula')).toBeInTheDocument();
  });

  it('una solicitud aprobada con la matrícula vencida lo dice en tono de peligro', () => {
    render(<EstadoSolicitudBadge status="APPROVED" admissionInvoice={factura({ paymentStatus: 'OVERDUE' })} />);
    const pill = screen.getByText('Matrícula vencida');
    expect(pill.className).toContain('text-danger');
  });

  it('una solicitud aprobada con la matrícula pagada figura como inscripto', () => {
    render(<EstadoSolicitudBadge status="APPROVED" admissionInvoice={factura({ paymentStatus: 'PAID' })} />);
    expect(screen.getByText('Inscripto').className).toContain('text-success');
  });

  it('una matrícula con comprobante en revisión figura como pago en revisión', () => {
    render(<EstadoSolicitudBadge status="APPROVED" admissionInvoice={factura({ receiptStatus: 'PENDING_REVIEW' })} />);
    expect(screen.getByText('Pago en revisión')).toBeInTheDocument();
  });
});
