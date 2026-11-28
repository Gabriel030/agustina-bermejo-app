import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TemplateLogoField } from './TemplateLogoField';

vi.mock('@/features/graduation/certificates/hooks/useCertificateTemplate', () => ({
  useUploadTemplateLogo: () => ({ isPending: false, mutateAsync: vi.fn() }),
  useTemplateImageUrl: (templateKey, imageId) => (imageId == null ? null : 'blob:own-logo'),
}));

describe('TemplateLogoField', () => {
  it('given no uploaded logo, when rendered, then shows the default institute logo preview and label', () => {
    renderWithProviders(<TemplateLogoField templateKey="PROGRAM" logoImageId={null} readOnly={false} onChange={() => {}} />);

    expect(screen.getByAltText('Logo del instituto por defecto')).toBeInTheDocument();
    expect(screen.getByText(/Logo del instituto \(por defecto\)/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Subir logo' })).toBeInTheDocument();
  });

  it('given an uploaded logo, when rendered, then shows the own logo instead of the default', () => {
    renderWithProviders(<TemplateLogoField templateKey="PROGRAM" logoImageId={9} readOnly={false} onChange={() => {}} />);

    expect(screen.getByAltText('Logo actual del certificado')).toHaveAttribute('src', 'blob:own-logo');
    expect(screen.queryByAltText('Logo del instituto por defecto')).not.toBeInTheDocument();
    expect(screen.queryByText(/por defecto/)).not.toBeInTheDocument();
  });
});
