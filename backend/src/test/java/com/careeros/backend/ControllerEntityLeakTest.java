package com.careeros.backend;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /dashboard has broken on this exact pattern three times now — a
 * controller (or a response type it returns) holding a JPA entity, whose
 * lazy proxy only fails once Jackson touches it after the owning
 * transaction has closed. That's a runtime 500, discovered whenever someone
 * next happens to hit the endpoint with data that exercises the unguarded
 * field. This makes it a compile-adjacent failure instead: every
 * {@code @RestController} handler method's return type is walked, including
 * generics (List/Optional/ResponseEntity/Map) and every declared field
 * reachable from it, and the build fails if a JPA {@code @Entity} is
 * reachable anywhere in that graph — regardless of whether any row exists
 * in the dev DB to trigger it at runtime.
 */
class ControllerEntityLeakTest {

    private static final String BASE_PACKAGE = "com.careeros.backend";

    @Test
    void noControllerReturnTypeTransitivelyHoldsAnEntity() {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : findControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || !isHandler(method)) continue;

                Class<?> entity = findEntity(method.getGenericReturnType(), new HashSet<>());
                if (entity != null) {
                    violations.add(controller.getSimpleName() + "#" + method.getName()
                            + " returns " + method.getGenericReturnType().getTypeName()
                            + " which holds entity " + entity.getSimpleName());
                }
            }
        }

        assertThat(violations).as("controller methods leaking JPA entities").isEmpty();
    }

    private static boolean isHandler(Method method) {
        for (Annotation a : method.getAnnotations()) {
            if (a.annotationType().getSimpleName().endsWith("Mapping")) return true;
        }
        return false;
    }

    /** Unwraps List/Set/Optional/ResponseEntity/Map<?, T>, then walks T's declared fields. */
    private static Class<?> findEntity(Type type, Set<Class<?>> visited) {
        Class<?> raw = rawClass(type);
        if (raw == null || raw.isPrimitive() || !visited.add(raw)) return null;

        if (raw.isAnnotationPresent(Entity.class)) return raw;

        if (isUnwrappable(raw) && type instanceof ParameterizedType pt) {
            for (Type arg : pt.getActualTypeArguments()) {
                Class<?> found = findEntity(arg, visited);
                if (found != null) return found;
            }
            return null;
        }

        if (isOpaque(raw)) return null;

        for (Field field : raw.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> found = findEntity(field.getGenericType(), visited);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isUnwrappable(Class<?> raw) {
        return List.class.isAssignableFrom(raw)
                || Set.class.isAssignableFrom(raw)
                || Map.class.isAssignableFrom(raw)
                || Optional.class.isAssignableFrom(raw)
                || org.springframework.http.ResponseEntity.class.isAssignableFrom(raw);
    }

    /** JDK types and enums never hold an entity worth walking into. */
    private static boolean isOpaque(Class<?> raw) {
        return raw.isEnum()
                || raw.getName().startsWith("java.")
                || raw.getName().startsWith("javax.");
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return rawClass(pt.getRawType());
        return null;
    }

    private static List<Class<?>> findControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> classes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                classes.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return classes;
    }
}
