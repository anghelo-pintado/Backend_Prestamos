# Prestamos API (Loan Management System)

Sistema de gestión de préstamos desarrollado con Java y Spring Boot. Esta aplicación permite administrar clientes, préstamos, cronogramas de pago, cajas, comprobantes y pagos, integrándose con servicios externos como RENIEC, SUNAT, ApisPeru (facturación electrónica) y MercadoPago.

## 🚀 Características

*   **Autenticación y Seguridad**: Registro e inicio de sesión de usuarios con Spring Security y JWT (JSON Web Tokens).
*   **Gestión de Clientes**: CRUD de clientes con validación de identidad mediante RENIEC.
*   **Gestión de Préstamos**: Creación de préstamos, cálculo de cuotas y seguimiento de estados.
*   **Pagos y Cobranzas**: Registro de pagos, integración con pasarela de pagos MercadoPago.
*   **Caja**: Apertura y cierre de caja, control de flujo de dinero.
*   **Comprobantes**: Generación de comprobantes electrónicos integrados con SUNAT/ApisPeru.
*   **Almacenamiento de Archivos**: Integración con DigitalOcean Spaces (S3 compatible) para almacenamiento de documentos.
*   **Base de Datos**: Soporte para H2 (Desarrollo) y PostgreSQL (Producción).

## 🛠️ Tecnologías

*   **Lenguaje**: Java 21
*   **Framework**: Spring Boot 3.5.6
    *   Spring Web (MVC)
    *   Spring Data JPA
    *   Spring Security
    *   Spring Validation
*   **Base de Datos**: H2 Database, PostgreSQL
*   **Herramientas**:
    *   Lombok
    *   Maven
*   **Servicios Externos**:
    *   MercadoPago SDK
    *   AWS SDK (S3) para DigitalOcean Spaces
    *   APIs de RENIEC y SUNAT

## 📋 Requisitos Previos

*   Java Development Kit (JDK) 21
*   Maven 3.8+

## ⚙️ Configuración

La aplicación requiere configurar varias variables de entorno para funcionar correctamente, especialmente para las integraciones externas.

Puedes configurar estas variables en tu sistema operativo o en un archivo `.env` (si usas alguna herramienta que lo soporte) o directamente en las propiedades de ejecución de tu IDE.

### Variables de Entorno Requeridas

| Variable | Descripción |
| --- | --- |
| `JWT_SECRET` | Clave secreta para firmar los tokens JWT. |
| `JWT_EXPIRATION` | Tiempo de expiración del token (ms). |
| `JWT_REFRESH_EXPIRATION` | Tiempo de expiración del refresh token (ms). |
| `RENIEC_API_URL` | URL de la API de RENIEC. |
| `RENIEC_API_KEY` | Clave de API para RENIEC. |
| `SUNAT_API_URL` | URL de la API de SUNAT. |
| `SUNAT_API_KEY` | Clave de API para SUNAT. |
| `UIT_PEN` | Valor de la UIT en Soles (PEN). |
| `MP_ACCESS_TOKEN` | Access Token de MercadoPago. |
| `MP_PUBLIC_KEY` | Public Key de MercadoPago. |
| `MP_WEBHOOK_URL` | URL para recibir notificaciones de MercadoPago. |
| `MP_BACK_URL_BASE` | URL base para redirecciones de MercadoPago. |
| `APISPERU_API_URL` | URL de ApisPeru. |
| `APISPERU_API_KEY` | Token/Key de ApisPeru. |
| `APISPERU_RUC_EMISOR` | RUC de la empresa emisora. |
| `APISPERU_RAZON_SOCIAL_EMISOR` | Razón social de la empresa. |
| `DO_SPACES_ACCESS_KEY` | Access Key de DigitalOcean Spaces. |
| `DO_SPACES_SECRET_KEY` | Secret Key de DigitalOcean Spaces. |
| `DO_SPACES_REGION` | Región del Space (ej. `nyc3`). |
| `DO_SPACES_BUCKET_NAME` | Nombre del Bucket. |
| `DO_SPACES_ENDPOINT_URL` | Endpoint URL (ej. `https://nyc3.digitaloceanspaces.com`). |
| `DATABASE_URL` | (Solo Prod) URL de conexión a PostgreSQL. |

## 🚀 Ejecución

### Desarrollo

El perfil de desarrollo (`dev`) utiliza una base de datos en memoria H2.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

O simplemente (ya que `dev` es el perfil por defecto en `application.yml`):

```bash
mvn spring-boot:run
```

Puedes acceder a la consola H2 en: `http://localhost:8080/h2-console`

### Producción

Para producción, asegúrate de configurar la variable `DATABASE_URL` y usar el perfil `prod`.

```bash
java -jar -Dspring.profiles.active=prod target/Prestamos-0.0.1-SNAPSHOT.jar
```

## 📦 Despliegue

El proyecto incluye un `Procfile` listo para el despliegue en plataformas como **Heroku**.

```
web: java -Dserver.port=$PORT -jar target/Prestamos-0.0.1-SNAPSHOT.jar
```

Asegúrate de configurar las variables de entorno (`Config Vars`) en el panel de control de tu proveedor de hosting.

## 📂 Estructura del Proyecto

El código fuente se encuentra bajo `src/main/java/com/a/prestamos`:

*   `controller`: Controladores REST (Endpoints).
*   `service`: Lógica de negocio e interfaces.
*   `model/entity`: Entidades JPA (Base de datos).
*   `model/dto`: Data Transfer Objects.
*   `repository`: Interfaces de acceso a datos (Spring Data JPA).
*   `security`: Configuración de seguridad y filtros JWT.
*   `client`: Clientes para servicios externos.

## 📄 Licencia

Este proyecto está bajo la Licencia MIT - mira el archivo [LICENSE.md](LICENSE.md) para detalles.
