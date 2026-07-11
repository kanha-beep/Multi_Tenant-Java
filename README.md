# serverj

Java backend clone of the existing `server` Node backend.

## Stack

- Spring Boot 2.7
- Spring Web
- Spring Data MongoDB
- JJWT

## Environment

Copy the values from the existing Node backend:

```properties
MONGO_URI=...
JWT_SECRET=...
FRONTEND_URL=...
PORT=3001
CLIENT_DIST_PATH=../client/dist
```

You can also place them in `src/main/resources/application.properties` via environment variables.

## Run

This workspace currently has a Java runtime but not a full JDK or Maven, so I could not compile it here.

Once Maven and a JDK are installed:

```bash
mvn spring-boot:run
```

Or build a jar:

```bash
mvn clean package
java -jar target/serverj-0.0.1-SNAPSHOT.jar
```

The API paths mirror the Node backend:

- `/api/homepage`
- `/api/studio`
- `/api/studio/capture`
- `/api/studio/focus/{taskId}`
- `/api/health`
- `/auth/*`
- `/notes/*`
- `/users/*`
- `/admin/*`

"# Multi_Tenant-Java" 
