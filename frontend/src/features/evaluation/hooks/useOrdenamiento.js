import { useSearchParams } from 'react-router-dom';

const MAX_CRITERIOS = 2;

// Orden por 1 o 2 criterios encadenados, clickeando headers de columna —
// pedido explícito del usuario ("elijo 1 o 2 criterios para ordenar").
// Reutilizable por cualquier tabla (no solo las de materias): cada tabla
// define sus propios campos ordenables y llama toggleOrden(id) desde el
// header correspondiente.
//
// Click en una columna sin orden activo: la agrega como criterio (ascendente,
// al final de la cadena — si ya hay 2, se descarta el más viejo). Click en una
// columna ya activa: invierte su dirección (asc → desc). Click de nuevo (3ra
// vez): la saca de la cadena. Estado en la URL (?sort=campo:dir,campo:dir),
// mismo criterio que el resto de los filtros de la app — recargable/compartible.
export function useOrdenamiento() {
  const [searchParams, setSearchParams] = useSearchParams();

  const criterios = (searchParams.get('sort') ?? '')
    .split(',')
    .filter(Boolean)
    .map((par) => {
      const [id, direccion] = par.split(':');
      return { id, direccion: direccion === 'desc' ? 'desc' : 'asc' };
    });

  function guardar(nuevosCriterios) {
    if (nuevosCriterios.length === 0) {
      searchParams.delete('sort');
    } else {
      searchParams.set('sort', nuevosCriterios.map((c) => `${c.id}:${c.direccion}`).join(','));
    }
    setSearchParams(searchParams, { replace: true });
  }

  function toggleOrden(id) {
    const existente = criterios.find((c) => c.id === id);
    let nuevos;
    if (!existente) {
      nuevos = [...criterios, { id, direccion: 'asc' }];
      if (nuevos.length > MAX_CRITERIOS) nuevos = nuevos.slice(nuevos.length - MAX_CRITERIOS);
    } else if (existente.direccion === 'asc') {
      nuevos = criterios.map((c) => (c.id === id ? { ...c, direccion: 'desc' } : c));
    } else {
      nuevos = criterios.filter((c) => c.id !== id);
    }
    guardar(nuevos);
  }

  function estadoDe(id) {
    const indice = criterios.findIndex((c) => c.id === id);
    if (indice === -1) return null;
    return { direccion: criterios[indice].direccion, prioridad: indice + 1 };
  }

  return { criterios, toggleOrden, estadoDe };
}
