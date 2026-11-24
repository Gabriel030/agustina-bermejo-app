import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Checkbox } from '@/components/ui/checkbox';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Toggle } from '@/components/ui/toggle';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { PasswordInput } from '@/components/ui/password-input';

describe('disabled states use tokens instead of opacity', () => {
  it('checkbox paints its border and fill with the disabled tokens', () => {
    render(<Checkbox disabled aria-label="Aceptar" />);
    const checkbox = screen.getByRole('checkbox', { name: 'Aceptar' });

    expect(checkbox).toHaveClass('disabled:border-(--disabled-fg)', 'disabled:bg-(--disabled-bg)', 'disabled:data-[state=checked]:bg-(--disabled-fg)');
    expect(checkbox.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('radio item paints its border and fill with the disabled tokens', () => {
    render(
      <RadioGroup>
        <RadioGroupItem value="a" disabled aria-label="Opción" />
      </RadioGroup>
    );
    const radio = screen.getByRole('radio', { name: 'Opción' });

    expect(radio).toHaveClass('disabled:border-(--disabled-fg)', 'disabled:bg-(--disabled-bg)', 'disabled:data-[state=checked]:border-(--disabled-fg)');
    expect(radio.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('switch track and thumb use the disabled tokens', () => {
    const { container } = render(<Switch disabled aria-label="Avisos" />);
    const track = screen.getByRole('switch', { name: 'Avisos' });
    const thumb = container.querySelector('[data-slot="switch-thumb"]');

    expect(track).toHaveClass('data-[disabled]:border-(--disabled-fg)', 'data-[disabled]:data-[state=unchecked]:bg-(--disabled-bg)');
    expect(thumb).toHaveClass('data-[disabled]:data-[state=unchecked]:bg-(--disabled-fg)');
    expect(track.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('tab trigger text uses the disabled token', () => {
    render(
      <Tabs defaultValue="a">
        <TabsList>
          <TabsTrigger value="a">Uno</TabsTrigger>
          <TabsTrigger value="b" disabled>Dos</TabsTrigger>
        </TabsList>
      </Tabs>
    );
    const trigger = screen.getByRole('tab', { name: 'Dos' });

    expect(trigger).toHaveClass('disabled:text-(--disabled-fg)');
    expect(trigger.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('toggle text uses the disabled token', () => {
    render(<Toggle disabled>Negrita</Toggle>);
    const toggle = screen.getByRole('button', { name: 'Negrita' });

    expect(toggle).toHaveClass('disabled:text-(--disabled-fg)');
    expect(toggle.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('label text uses the disabled token next to a disabled control', () => {
    render(<Label>Nombre</Label>);
    const label = screen.getByText('Nombre');

    expect(label).toHaveClass('peer-disabled:text-(--disabled-fg)', 'group-data-[disabled=true]:text-(--disabled-fg)');
    expect(label.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('textarea uses the disabled background and text tokens', () => {
    render(<Textarea disabled aria-label="Notas" />);
    const textarea = screen.getByRole('textbox', { name: 'Notas' });

    expect(textarea).toHaveClass('disabled:bg-(--disabled-bg)', 'disabled:text-(--disabled-fg)');
    expect(textarea.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });

  it('password toggle button uses the disabled text token', () => {
    render(<PasswordInput disabled aria-label="Clave" />);
    const toggle = screen.getByRole('button', { name: 'Mostrar contraseña' });

    expect(toggle).toHaveClass('disabled:text-(--disabled-fg)');
    expect(toggle.className).not.toMatch(/opacity-(40|50)(\s|$)/);
  });
});
