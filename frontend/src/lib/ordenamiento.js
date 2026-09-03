// Motor de ordenamiento genérico y reutilizable — nuestra propia versión
// (liviana, sin dependencia externa) de lo que una librería tipo TanStack
// Table ofrece para esto: click en una columna para ordenar, encadenado con
// otra columna (hasta 2 criterios). Se probó @tanstack/react-table y se
// descartó por un freeze reproducible del navegador sin diagnóstico claro —
// ver el comentario en TabMaterias.jsx. Usado por useOrdenamiento.js.
//
// `definiciones` es un array de { id, accessor(fila) => valor comparable }.
// `criterios` es el array de { id, direccion: 'asc'|'desc' } que devuelve
// useOrdenamiento, en orden de prioridad (el primero decide, el segundo
// desempata).
export function construirComparador(criterios, definiciones) {
  return (a, b) => {
    for (const { id, direccion } of criterios) {
      const definicion = definiciones.find((d) => d.id === id);
      if (!definicion) continue;
      const valorA = definicion.accessor(a);
      const valorB = definicion.accessor(b);
      let comparacion;
      if (valorA == null && valorB == null) comparacion = 0;
      else if (valorA == null) comparacion = 1;
      else if (valorB == null) comparacion = -1;
      else if (typeof valorA === 'string') comparacion = valorA.localeCompare(valorB);
      else comparacion = valorA - valorB;
      if (direccion === 'desc') comparacion = -comparacion;
      if (comparacion !== 0) return comparacion;
    }
    return 0;
  };
}
