# Aplicación de Gestión Educativa

Aplicación web para la gestión de una institución educativa, desarrollada con Spring Boot en el backend y React en el frontend.

## 🏗️ Arquitectura

El proyecto sigue una arquitectura de tres capas:

### Backend (Spring Boot)
- **Capa de Entidades**: Modelos de datos que representan las tablas de la base de datos.
- **Repositorios**: Interfaces que extienden JpaRepository para el acceso a datos.
- **Servicios**: Lógica de negocio y reglas de la aplicación.
- **Controladores**: Manejan las peticiones HTTP y devuelven respuestas.
- **DTOs**: Objetos para transferencia de datos entre capas.
- **Seguridad**: Autenticación y autorización con JWT.

### Frontend (React + Vite)
- **Componentes**: UI reutilizables.
- **Vistas**: Páginas principales de la aplicación.
- **Servicios**: Llamadas a la API del backend.
- **Contexto**: Gestión del estado global de la aplicación.

### Base de Datos
- MySQL/PostgreSQL para almacenamiento persistente.
- Migraciones con Flyway.

## 🗄️ Modelo de Datos

A continuación se muestra el diagrama entidad-relación del sistema:

```
[PEGAR AQUÍ LA IMAGEN DEL MODELO DE DATOS]
```

## 🚀 Características Principales

- Gestión de estudiantes y docentes
- Administración de cursos y materias
- Sistema de inscripciones
- Gestión de calificaciones
- Programas de estudio
- Eventos institucionales

## 🛠️ Tecnologías Utilizadas

### Backend
- Java 17
- Spring Boot 3.x
- Spring Security
- JPA/Hibernate
- JWT para autenticación
- Maven

### Frontend
- React 18
- Vite
- TypeScript
- Tailwind CSS
- React Router
- Axios

## 📦 Requisitos Previos

- Java 17 o superior
- Node.js 16+ y npm/yarn
- Base de datos MySQL/PostgreSQL
- Maven 3.6+

## 🚀 Instalación

1. Clonar el repositorio
2. Configurar la base de datos en `backend/src/main/resources/application.properties`
3. Ejecutar el backend:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
4. Ejecutar el frontend:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## 📝 Licencia

Este proyecto está bajo la Licencia MIT.
