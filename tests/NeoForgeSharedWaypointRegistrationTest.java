// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/** Invokes the production registration against the native NeoForge registry in a fresh JVM. */
public final class NeoForgeSharedWaypointRegistrationTest {
    public static void main(String[] args) throws Exception {
        Class<?> eventClass = Class.forName("net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent");
        Object event = eventClass.getConstructor().newInstance();
        Class<?> network = Class.forName("net.aosankaku.openmapdelta.network.NeoForgeNetwork");
        Method register = network.getDeclaredMethod("registerPayloads", eventClass);
        register.setAccessible(true);
        register.invoke(null, event);

        Class<?> protocol = Class.forName("net.minecraft.network.ConnectionProtocol");
        Object play = protocol.getField("PLAY").get(null);
        Class<?> registry = Class.forName("net.neoforged.neoforge.network.registration.NetworkRegistry");
        var registrationsField = registry.getDeclaredField("PAYLOAD_REGISTRATIONS");
        registrationsField.setAccessible(true);
        Map<?, ?> registrations = (Map<?, ?>) registrationsField.get(null);
        Map<?, ?> playRegistrations = (Map<?, ?>) registrations.get(play);
        for (String payloadName : new String[] {"SharedWaypointPayload", "LocationContextPayload"}) {
            Class<?> payload = Class.forName("net.aosankaku.openmapdelta.network." + payloadName);
            Object type = payload.getField("TYPE").get(null);
            Object id = type.getClass().getMethod("id").invoke(type);
            Object registered = playRegistrations.get(id);
            if (registered == null) throw new AssertionError(payloadName + " was not registered");
            Optional<?> flow = (Optional<?>) registered.getClass().getMethod("flow").invoke(registered);
            if (flow.isPresent()) throw new AssertionError(payloadName + " must permit both packet directions");
            if (!Boolean.TRUE.equals(registered.getClass().getMethod("optional").invoke(registered)))
                throw new AssertionError(payloadName + " must remain optional for older/vanilla peers");
        }
        int additional = 0;
        for (String payloadName : new String[] {"DeathPointPayload", "ServerConfigRequestPayload", "ServerConfigSnapshotPayload"}) {
            Class<?> payload;
            try { payload = Class.forName("net.aosankaku.openmapdelta.network." + payloadName); }
            catch (ClassNotFoundException olderTarget) { continue; }
            Object type = payload.getField("TYPE").get(null);
            Object id = type.getClass().getMethod("id").invoke(type);
            Object registered = playRegistrations.get(id);
            if (registered == null) throw new AssertionError(payloadName + " was not registered");
            Optional<?> flow = (Optional<?>) registered.getClass().getMethod("flow").invoke(registered);
            String expected = payloadName.equals("ServerConfigRequestPayload") ? "SERVERBOUND" : "CLIENTBOUND";
            if (flow.isEmpty() || !expected.equals(flow.get().toString()))
                throw new AssertionError(payloadName + " has an incorrect packet direction");
            if (!Boolean.TRUE.equals(registered.getClass().getMethod("optional").invoke(registered)))
                throw new AssertionError(payloadName + " must remain optional");
            additional++;
        }
        System.out.println("NeoForge native shared/context registration: two optional bidirectional play payloads and "
                + additional + " optional directional death/config payloads passed.");
    }
}
