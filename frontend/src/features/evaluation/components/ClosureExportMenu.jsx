import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { EXPORT_FORMATS } from '../lib/closureSheet';

export function ClosureExportMenu({ closed, pending, onExport }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline" loading={pending}>
          <Download className="size-4" aria-hidden="true" /> Exportar
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64 bg-dialog-bg backdrop-blur-[28px] backdrop-saturate-[1.6]">
        <DropdownMenuItem onSelect={() => onExport('xlsx')}>{EXPORT_FORMATS.xlsx.label}</DropdownMenuItem>
        <DropdownMenuItem disabled={!closed} onSelect={() => onExport('pdf')}>
          {EXPORT_FORMATS.pdf.label}
          {!closed && <span className="ml-1 text-xs text-ink-faint">(al cerrar)</span>}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
