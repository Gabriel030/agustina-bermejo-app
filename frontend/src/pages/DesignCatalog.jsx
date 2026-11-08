import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Popover, PopoverContent, PopoverDescription, PopoverHeader, PopoverTitle, PopoverTrigger } from '@/components/ui/popover';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet';
import { Switch } from '@/components/ui/switch';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { GlassCard } from '@/components/GlassCard';
import { ProgressBar } from '@/components/ProgressBar';
import { Skeleton } from '@/components/Skeleton';
import { StatCard } from '@/components/StatCard';
import { StatusPill } from '@/components/StatusPill';
import { Stepper } from '@/components/Stepper';
import { ThemeToggle } from '@/components/ThemeToggle';

const BUTTON_VARIANTS = ['default', 'outline', 'secondary', 'ghost', 'destructive', 'link', 'gradient'];
const BUTTON_SIZES = ['default', 'sm', 'lg'];
const PILL_TONES = ['success', 'warning', 'danger', 'accent', 'neutral'];
const BADGE_VARIANTS = ['default', 'secondary', 'destructive', 'outline', 'ghost'];
const PROGRESS_TONES = ['accent', 'success', 'danger'];
const STEPS = [
  { key: 'data', label: 'Datos' },
  { key: 'documents', label: 'Documentos' },
  { key: 'payment', label: 'Pago' },
  { key: 'confirm', label: 'Confirmación' },
];
const TABLE_ROWS = [
  { id: 1, student: 'Lucía Fernández', career: 'Profesorado de Inglés', status: 'success', label: 'Al día' },
  { id: 2, student: 'Martín Gómez', career: 'Tecnicatura en Programación', status: 'warning', label: 'Pendiente' },
  { id: 3, student: 'Sofía Ramírez', career: 'Profesorado de Inglés', status: 'danger', label: 'Vencido' },
  { id: 4, student: 'Tomás Acosta', career: 'Tecnicatura en Programación', status: 'neutral', label: 'Sin datos' },
];

function Section({ id, title, children }) {
  const headingId = `catalog-heading-${id}`;
  return (
    <section aria-labelledby={headingId} data-testid={`catalog-${id}`} className="flex flex-col gap-4 p-4">
      <h2 id={headingId} className="text-lg font-semibold text-ink">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Row({ children }) {
  return <div className="flex flex-wrap items-center gap-3">{children}</div>;
}

function ExampleDialog({ open, onOpenChange }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button variant="gradient">Abrir diálogo</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirmar inscripción</DialogTitle>
          <DialogDescription>Vas a inscribirte a la comisión elegida. Podés cancelarla hasta el inicio de clases.</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline">Cancelar</Button>
          <Button variant="gradient">Confirmar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function DesignCatalog() {
  const [searchParams, setSearchParams] = useSearchParams();
  const openTarget = searchParams.get('open');

  useEffect(() => {
    if (openTarget === 'toast') toast.success('Cambios guardados', { description: 'La comisión se actualizó correctamente.' });
  }, [openTarget]);

  function setOpenTarget(name, isOpen) {
    const next = new URLSearchParams(searchParams);
    if (isOpen) next.set('open', name);
    else next.delete('open');
    setSearchParams(next, { replace: true });
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-4 p-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-ink">Catálogo de componentes</h1>
        <ThemeToggle />
      </header>

      <Section id="buttons" title="Botones">
        {BUTTON_VARIANTS.map((variant) => (
          <Row key={variant}>
            {BUTTON_SIZES.map((size) => (
              <Button key={size} variant={variant} size={size}>
                {variant} {size}
              </Button>
            ))}
            <Button variant={variant} size="icon" aria-label={`Ícono ${variant}`}>
              +
            </Button>
            <Button variant={variant} disabled>
              Deshabilitado
            </Button>
            <Button variant={variant} loading>
              Cargando
            </Button>
          </Row>
        ))}
      </Section>

      <Section id="cards" title="Tarjetas">
        <div className="grid gap-4 sm:grid-cols-2">
          <GlassCard className="p-5">Tarjeta de vidrio</GlassCard>
          <GlassCard interactive className="p-5">
            Tarjeta interactiva
          </GlassCard>
          <StatCard label="Alumnos activos">
            <span className="text-2xl font-bold text-ink">1.284</span>
          </StatCard>
          <Card>
            <CardHeader>
              <CardTitle>Tarjeta base</CardTitle>
              <CardDescription>Descripción de ejemplo</CardDescription>
            </CardHeader>
            <CardContent>Contenido de la tarjeta</CardContent>
          </Card>
        </div>
      </Section>

      <Section id="pills" title="Etiquetas de estado">
        <Row>
          {PILL_TONES.map((tone) => (
            <StatusPill key={tone} tone={tone}>
              {tone}
            </StatusPill>
          ))}
        </Row>
        <Row>
          {PILL_TONES.map((tone) => (
            <StatusPill key={tone} tone={tone} dot>
              {tone}
            </StatusPill>
          ))}
        </Row>
        <Row>
          {BADGE_VARIANTS.map((variant) => (
            <Badge key={variant} variant={variant}>
              {variant}
            </Badge>
          ))}
        </Row>
      </Section>

      <Section id="tabs" title="Pestañas">
        <Tabs defaultValue="summary">
          <TabsList variant="segmented">
            <TabsTrigger value="summary">Resumen</TabsTrigger>
            <TabsTrigger value="students">Alumnos</TabsTrigger>
            <TabsTrigger value="grades">Notas</TabsTrigger>
            <TabsTrigger value="payments">Pagos</TabsTrigger>
          </TabsList>
        </Tabs>
      </Section>

      <Section id="inputs" title="Campos">
        <div className="grid gap-3 sm:grid-cols-2">
          <Input placeholder="Nombre y apellido" aria-label="Campo vacío" />
          <Input defaultValue="Lucía Fernández" aria-label="Campo con valor" />
          <Input defaultValue="correo-invalido" aria-invalid="true" aria-label="Campo inválido" />
          <Input defaultValue="No editable" disabled aria-label="Campo deshabilitado" />
          <Input defaultValue="Solo lectura" readOnly aria-label="Campo de solo lectura" />
          <PasswordInput defaultValue="secreto123" aria-label="Contraseña" />
        </div>
        <Textarea defaultValue="Observaciones de ejemplo para la comisión." aria-label="Observaciones" />
        <Row>
          <label className="flex items-center gap-2 text-sm text-ink">
            <Checkbox defaultChecked /> Marcado
          </label>
          <label className="flex items-center gap-2 text-sm text-ink">
            <Checkbox /> Sin marcar
          </label>
          <label className="flex items-center gap-2 text-sm text-ink">
            <Switch defaultChecked /> Activado
          </label>
          <label className="flex items-center gap-2 text-sm text-ink">
            <Switch /> Desactivado
          </label>
        </Row>
      </Section>

      <Section id="selects" title="Selectores">
        <div className="grid gap-3 sm:grid-cols-2">
          <Select defaultValue="first">
            <SelectTrigger aria-label="Selector cerrado">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="first">Primer cuatrimestre</SelectItem>
              <SelectItem value="second">Segundo cuatrimestre</SelectItem>
            </SelectContent>
          </Select>
          <Select
            defaultValue="second"
            open={openTarget === 'select'}
            onOpenChange={(isOpen) => setOpenTarget('select', isOpen)}
          >
            <SelectTrigger aria-label="Selector abierto">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="first">Primer cuatrimestre</SelectItem>
              <SelectItem value="second">Segundo cuatrimestre</SelectItem>
              <SelectItem value="third">Verano</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </Section>

      <Section id="table" title="Tabla">
        <GlassCard className="overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Alumno</TableHead>
                <TableHead>Carrera</TableHead>
                <TableHead>Estado</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {TABLE_ROWS.map((row) => (
                <TableRow key={row.id} data-state={row.id === 2 ? 'selected' : undefined}>
                  <TableCell>{row.student}</TableCell>
                  <TableCell>{row.career}</TableCell>
                  <TableCell>
                    <StatusPill tone={row.status}>{row.label}</StatusPill>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </GlassCard>
      </Section>

      <Section id="dialog" title="Diálogo">
        <ExampleDialog open={openTarget === 'dialog'} onOpenChange={(isOpen) => setOpenTarget('dialog', isOpen)} />
      </Section>

      <Section id="overlays" title="Paneles y avisos">
        <Row>
          <Popover open={openTarget === 'popover'} onOpenChange={(isOpen) => setOpenTarget('popover', isOpen)}>
            <PopoverTrigger asChild>
              <Button variant="outline">Abrir popover</Button>
            </PopoverTrigger>
            <PopoverContent>
              <PopoverHeader>
                <PopoverTitle>Filtros</PopoverTitle>
                <PopoverDescription>Elegí cómo ordenar el listado.</PopoverDescription>
              </PopoverHeader>
            </PopoverContent>
          </Popover>
          <Sheet open={openTarget === 'sheet'} onOpenChange={(isOpen) => setOpenTarget('sheet', isOpen)}>
            <SheetTrigger asChild>
              <Button variant="outline">Abrir panel lateral</Button>
            </SheetTrigger>
            <SheetContent>
              <SheetHeader>
                <SheetTitle>Detalle de la comisión</SheetTitle>
                <SheetDescription>Información de ejemplo del panel lateral.</SheetDescription>
              </SheetHeader>
            </SheetContent>
          </Sheet>
          <Button variant="outline" onClick={() => toast.success('Cambios guardados')}>
            Mostrar aviso
          </Button>
        </Row>
      </Section>

      <Section id="skeleton" title="Carga">
        <div className="flex flex-col gap-3">
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-6 w-full" />
        </div>
      </Section>

      <Section id="stepper" title="Pasos">
        <Stepper pasos={STEPS} pasoActual="documents" />
      </Section>

      <Section id="progress" title="Progreso">
        <div className="flex flex-col gap-3">
          {PROGRESS_TONES.map((tone, index) => (
            <ProgressBar key={tone} tone={tone} value={30 + index * 25} label={`Avance ${tone}`} />
          ))}
        </div>
      </Section>
    </div>
  );
}
