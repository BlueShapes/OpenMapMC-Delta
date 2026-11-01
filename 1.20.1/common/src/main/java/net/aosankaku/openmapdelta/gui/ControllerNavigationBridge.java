// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Optional, reflection-only adapter for Controllable's navigation point event. */
public final class ControllerNavigationBridge {
    private static final String EVENTS_CLASS = "com.mrcrayfish.controllable.event.ControllerEvents";
    private static final String LISTENER_CLASS = EVENTS_CLASS + "$GatherNavigationPoints";
    private static final String POINT_CLASS = "com.mrcrayfish.controllable.client.gui.navigation.BasicNavigationPoint";

    private Object registeredEvent;
    private Object listenerProxy;
    private boolean registrationAttempted;
    private boolean connected;
    private boolean failureReported;
    private Consumer<Throwable> failureConsumer;

    /**
     * Connects to Controllable when the optional mod is present. A missing event
     * class is a normal absence and is therefore silent.
     */
    public boolean connect(ClassLoader loader, Supplier<List<ControllerNavigationLayout.Point>> provider,
            Consumer<Throwable> onFailure) {
        rememberFailureConsumer(onFailure);
        synchronized (this) {
            if (connected) {
                return true;
            }
            if (registrationAttempted) {
                return false;
            }
        }
        try {
            Class<?> eventsClass = Class.forName(EVENTS_CLASS, false, loader);
            Class<?> listenerInterface = Class.forName(LISTENER_CLASS, false, loader);
            Class<?> pointClass = Class.forName(POINT_CLASS, false, loader);
            Field eventField = eventsClass.getField("GATHER_NAVIGATION_POINTS");
            if (!Modifier.isStatic(eventField.getModifiers())) {
                reportFailure(new IllegalStateException("GATHER_NAVIGATION_POINTS is not static"));
                return false;
            }
            Object event = eventField.get(null);
            Constructor<?> pointConstructor = pointClass.getConstructor(double.class, double.class);
            return connect(event, listenerInterface, pointConstructor, provider, onFailure);
        } catch (ClassNotFoundException absentOptionalMod) {
            return false;
        } catch (Throwable failure) {
            rethrowIfFatal(failure);
            reportFailure(unwrap(failure));
            return false;
        }
    }

    /** Exposed for isolated tests and callers that already have optional API types. */
    public boolean connect(Object event, Class<?> listenerInterface, Constructor<?> pointConstructor,
            Supplier<List<ControllerNavigationLayout.Point>> provider, Consumer<Throwable> onFailure) {
        rememberFailureConsumer(onFailure);
        synchronized (this) {
            if (connected) {
                return registeredEvent == event;
            }
            if (registrationAttempted) {
                return false;
            }

            try {
                Objects.requireNonNull(event, "event");
                Objects.requireNonNull(provider, "provider");
                Objects.requireNonNull(pointConstructor, "pointConstructor");
                validateListener(listenerInterface);
                Method registerMethod = findRegisterMethod(event.getClass(), listenerInterface);
                InvocationHandler handler = createHandler(pointConstructor, provider);
                Object proxy = Proxy.newProxyInstance(listenerInterface.getClassLoader(),
                        new Class<?>[] { listenerInterface }, handler);

                // Retain the proxy before invoking registration. Some event sources
                // can store it and then throw while completing the registration.
                registeredEvent = event;
                listenerProxy = proxy;
                registrationAttempted = true;
                registerMethod.invoke(event, proxy);
                connected = true;
                return true;
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                reportFailure(unwrap(failure));
                return false;
            }
        }
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    private InvocationHandler createHandler(Constructor<?> pointConstructor,
            Supplier<List<ControllerNavigationLayout.Point>> provider) {
        return (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, arguments);
            }
            if (!isHandleMethod(method)) {
                reportFailure(new IllegalStateException("Unexpected navigation callback: " + method));
                return defaultValue(method.getReturnType());
            }

            if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof List<?> target)) {
                reportFailure(new IllegalArgumentException("Navigation callback must receive a List"));
                return null;
            }
            try {
                List<ControllerNavigationLayout.Point> points = provider.get();
                if (points == null) {
                    return null;
                }
                List<Object> replacement = new ArrayList<>(points.size());
                for (ControllerNavigationLayout.Point point : points) {
                    Objects.requireNonNull(point, "navigation point");
                    replacement.add(pointConstructor.newInstance(point.x(), point.y()));
                }
                @SuppressWarnings("unchecked")
                List<Object> mutableTarget = (List<Object>) target;
                mutableTarget.clear();
                mutableTarget.addAll(replacement);
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                reportFailure(unwrap(failure));
            }
            return null;
        };
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == (arguments == null ? null : arguments[0]);
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "ControllerNavigationBridge.Listener@"
                    + Integer.toHexString(System.identityHashCode(proxy));
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        return 0.0d;
    }

    private static boolean isHandleMethod(Method method) {
        return method.getName().equals("handle") && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == List.class && method.getReturnType() == void.class;
    }

    private static void validateListener(Class<?> listenerInterface) {
        if (listenerInterface == null || !listenerInterface.isInterface()) {
            throw new IllegalArgumentException("Navigation listener must be an interface");
        }
        Method handle;
        try {
            handle = listenerInterface.getMethod("handle", List.class);
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("Navigation listener must expose handle(List)", failure);
        }
        if (!isHandleMethod(handle)) {
            throw new IllegalArgumentException("Navigation listener handle(List) must return void");
        }
        int abstractMethods = 0;
        for (Method method : listenerInterface.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) && method.getDeclaringClass() != Object.class) {
                abstractMethods++;
                if (!isHandleMethod(method)) {
                    throw new IllegalArgumentException("Unexpected abstract listener method: " + method);
                }
            }
        }
        if (abstractMethods != 1) {
            throw new IllegalArgumentException("Navigation listener must be functional");
        }
    }

    private static Method findRegisterMethod(Class<?> eventClass, Class<?> listenerInterface) {
        Method selected = null;
        for (Method method : eventClass.getMethods()) {
            if (!method.getName().equals("register") || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isAssignableFrom(listenerInterface)) {
                continue;
            }
            if (selected == null || selected.getParameterTypes()[0]
                    .isAssignableFrom(method.getParameterTypes()[0])) {
                selected = method;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Event has no compatible public register(listener) method");
        }
        return selected;
    }

    private void rememberFailureConsumer(Consumer<Throwable> consumer) {
        if (consumer == null) return;
        synchronized (this) {
            if (!failureReported) failureConsumer = consumer;
        }
    }

    private void reportFailure(Throwable failure) {
        Consumer<Throwable> consumer;
        synchronized (this) {
            if (failureReported) return;
            failureReported = true;
            consumer = failureConsumer;
        }
        if (consumer == null) return;
        try {
            consumer.accept(failure);
        } catch (Throwable ignored) {
            // Error reporting must never break optional input dispatch.
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof InvocationTargetException || failure instanceof ExceptionInInitializerError)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
        if (failure instanceof VirtualMachineError vmError) throw vmError;
    }
}
