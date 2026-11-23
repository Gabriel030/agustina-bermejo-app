import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { apiFetch, apiFetchBlob } from '@/api/client';
import { tabOf, templatePath } from '@/features/graduation/certificates/lib/certificateTemplate';

export const TEMPLATE_KEY = ['certificate-templates'];

export function useCertificateTemplate(templateKey) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, templateKey],
    queryFn: () => apiFetch(templatePath(templateKey)),
  });
}

export function useSaveTemplateDraft(templateKey) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content) => apiFetch(`${templatePath(templateKey)}/draft`, { method: 'PUT', body: JSON.stringify({ content }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, templateKey] }),
  });
}

export function useDiscardTemplateDraft(templateKey) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`${templatePath(templateKey)}/draft`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, templateKey] }),
  });
}

export function useTemplateVersions(templateKey, enabled = true) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, templateKey, 'versions'],
    queryFn: () => apiFetch(`${templatePath(templateKey)}/versions`),
    enabled,
  });
}

export function useTemplateVersion(templateKey, number) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, templateKey, 'versions', number],
    queryFn: () => apiFetch(`${templatePath(templateKey)}/versions/${number}`),
    enabled: number != null,
  });
}

export function useLoadTemplateVersion(templateKey) {
  return useMutation({
    mutationFn: (number) => apiFetch(`${templatePath(templateKey)}/versions/${number}`),
  });
}

export function usePublishTemplate(templateKey) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch(`${templatePath(templateKey)}/publish`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [...TEMPLATE_KEY, templateKey] }),
  });
}

export function useTemplatePreview(templateKey, source, stamp) {
  return useQuery({
    queryKey: [...TEMPLATE_KEY, templateKey, 'preview', source, stamp],
    queryFn: async () => {
      const { blob } = await apiFetchBlob(`${templatePath(templateKey)}/preview`, {
        method: 'POST',
        body: JSON.stringify({ source }),
      });
      return blob;
    },
    enabled: source != null,
    staleTime: Infinity,
    gcTime: 0,
  });
}

export function useUploadTemplateLogo(templateKey) {
  return useMutation({
    mutationFn: (file) => {
      const form = new FormData();
      form.append('file', file);
      return apiFetch(`${templatePath(templateKey)}/images?purpose=LOGO`, { method: 'POST', body: form });
    },
  });
}

export function useTemplateSignatories(templateKey) {
  const documentType = tabOf(templateKey).signatoryDocumentType;
  return useQuery({
    queryKey: ['certificate-signatories', documentType, 'active'],
    queryFn: () => apiFetch(`/certificate-signatories?documentType=${documentType}&active=true&size=100`),
    select: (page) => page.content,
  });
}

export function useTemplateImageUrl(templateKey, imageId) {
  const [state, setState] = useState({ key: null, url: null });
  const key = imageId == null ? null : `${templateKey}:${imageId}`;
  useEffect(() => {
    if (key == null) return undefined;
    let cancelled = false;
    let objectUrl = null;
    apiFetchBlob(`${templatePath(templateKey)}/images/${imageId}`)
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
  }, [key, templateKey, imageId]);
  return state.key === key ? state.url : null;
}
