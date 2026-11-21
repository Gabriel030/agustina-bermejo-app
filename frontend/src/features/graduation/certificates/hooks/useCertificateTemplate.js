import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { SIGNATORY_DOCUMENT_TYPE } from '@/features/graduation/certificates/lib/certificateTemplate';

export const TEMPLATE_KEY = ['certificate-templates'];

export function useCertificateTemplate(kind) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, kind],
    queryFn: () => apiFetch(`/certificate-templates/${kind}`),
  });
}

export function useSaveTemplateDraft(kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content) => apiFetch(`/certificate-templates/${kind}/draft`, { method: 'PUT', body: JSON.stringify({ content }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, kind] }),
  });
}

export function useDiscardTemplateDraft(kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/certificate-templates/${kind}/draft`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, kind] }),
  });
}

export function useTemplateVersions(kind) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, kind, 'versions'],
    queryFn: () => apiFetch(`/certificate-templates/${kind}/versions`),
  });
}

export function useTemplateVersion(kind, number) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, kind, 'versions', number],
    queryFn: () => apiFetch(`/certificate-templates/${kind}/versions/${number}`),
    enabled: number != null,
  });
}

export function usePublishTemplate(kind) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`/certificate-templates/${kind}/publish`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, kind] }),
  });
}

export function usePreviewTemplate(kind) {
  return useMutation({
    mutationFn: (source) =>
      apiFetchBlob(`/certificate-templates/${kind}/preview`, { method: 'POST', body: JSON.stringify({ source }) }),
  });
}

export function useUploadTemplateLogo(kind) {
  return useMutation({
    mutationFn: (file) => {
      const form = new FormData();
      form.append('file', file);
      return apiFetch(`/certificate-templates/${kind}/images?purpose=LOGO`, { method: 'POST', body: form });
    },
  });
}

export function useGraduationSignatories() {
  return useQuery({
    queryKey: ['certificate-signatories', SIGNATORY_DOCUMENT_TYPE, 'active'],
    queryFn: () =>
      apiFetch(`/certificate-signatories?documentType=${SIGNATORY_DOCUMENT_TYPE}&active=true&size=100`),
    select: (page) => page.content,
  });
}

export function useTemplateImageUrl(kind, imageId) {
  const [state, setState] = useState({ key: null, url: null });
  const key = imageId == null ? null : `${kind}:${imageId}`;
  useEffect(() => {
    if (key == null) return undefined;
    let cancelled = false;
    let objectUrl = null;
    apiFetchBlob(`/certificate-templates/${kind}/images/${imageId}`)
      .then(({ blob }) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setState({ key, url: objectUrl });
      })
      .catch(() => {
        if (!cancelled) setState({ key, url: null });
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [key, kind, imageId]);
  return state.key === key ? state.url : null;
}
