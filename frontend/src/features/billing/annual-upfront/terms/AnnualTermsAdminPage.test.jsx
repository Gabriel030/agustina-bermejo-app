import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { createDeferred } from '@/test/deferred';
import { errorBody } from '@/test/fixtures/errors';
import { auditedAnnualTerms } from '@/test/fixtures/annualTerms';
import { AnnualTermsAdminPage } from './AnnualTermsAdminPage';

const API = 'http://localhost:8080';
const NEW_TEXT = 'a'.repeat(60);

function versions() {
  return [
    auditedAnnualTerms({ versionNumber: 2, version: 'v2', publishedByPersonId: 7, publishedAt: '2027-03-01T08:00:00' }),
    auditedAnnualTerms({ current: false, publishedByPersonId: 12 }),
  ];
}

async function mountReady() {
  server.use(http.get(`${API}/annual-upfront-terms/versions`, () => HttpResponse.json(versions())));
  const user = userEvent.setup();
  renderWithProviders(<AnnualTermsAdminPage />);
  await screen.findByText('Versiones publicadas');
  return user;
}

describe('AnnualTermsAdminPage', () => {
  installFixedClock();

  it('listsVersionsWithPillsAndPublisherIdWithoutInventingNames', async () => {
    await mountReady();
    const rows = screen.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('v2')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Vigente')).toBeInTheDocument();
    expect(within(rows[0]).getByText('Persona #7')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Reemplazada')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Persona #12')).toBeInTheDocument();
  });

  it('prefillsEditorWithLatestTextAndCountsCharacters', async () => {
    const user = await mountReady();
    const editor = screen.getByRole('textbox', { name: 'Texto de los términos' });
    expect(editor).toHaveValue(versions()[0].text);
    await user.clear(editor);
    await user.type(editor, 'corto');
    expect(screen.getByText(/5 de 20000 caracteres/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar nueva versión' })).toBeDisabled();
  });

  it('publishesAfterConfirmingTheIrreversibleDialog', async () => {
    const sent = createDeferred();
    server.use(
      http.post(`${API}/annual-upfront-terms/versions`, async ({ request }) => {
        sent.resolve(await request.json());
        return HttpResponse.json(auditedAnnualTerms({ versionNumber: 3, version: 'v3' }), { status: 201 });
      }),
    );
    const user = await mountReady();
    const editor = screen.getByRole('textbox', { name: 'Texto de los términos' });
    await user.clear(editor);
    await user.type(editor, NEW_TEXT);
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('inmutables');
    await user.click(within(dialog).getByRole('button', { name: 'Publicar' }));
    expect(await sent.promise).toEqual({ text: NEW_TEXT });
  });

  it('keepsTheDialogClosedWithoutPostingWhenCancelled', async () => {
    let posted = false;
    server.use(
      http.post(`${API}/annual-upfront-terms/versions`, () => {
        posted = true;
        return HttpResponse.json(auditedAnnualTerms(), { status: 201 });
      }),
    );
    const user = await mountReady();
    const editor = screen.getByRole('textbox', { name: 'Texto de los términos' });
    await user.clear(editor);
    await user.type(editor, NEW_TEXT);
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it('showsServerMessageWhenPublishFails', async () => {
    server.use(
      http.post(`${API}/annual-upfront-terms/versions`, () =>
        HttpResponse.json(errorBody('VALIDATION_ERROR'), { status: 400 }),
      ),
    );
    const user = await mountReady();
    const editor = screen.getByRole('textbox', { name: 'Texto de los términos' });
    await user.clear(editor);
    await user.type(editor, NEW_TEXT);
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));
    await user.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Publicar' }));
    expect(await screen.findByRole('alert')).not.toBeEmptyDOMElement();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });
});
