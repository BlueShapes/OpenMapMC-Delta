import net.aosankaku.openmapdelta.client.ClientEvents;
import net.minecraft.client.player.LocalPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Invokes the production NeoForge Clone handler without starting a game client. */
public final class NeoForgeClientRespawnTest {
    private static final String CLIENT_CLASS = "net.aosankaku.openmapdelta.OpenMapDeltaNeoForgeClient";
    private static final String CLONE_CLASS = "net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent$Clone";

    private NeoForgeClientRespawnTest() {}

    public static void main(String[] args) throws Exception {
        LocalPlayer deadPlayer = new LocalPlayer(true, "minecraft:overworld", 104.25, -37.75);
        LocalPlayer respawnedPlayer = new LocalPlayer(false, "minecraft:the_nether", 8.5, 12.5);
        ClientEvents deathEvents = invokeClone(deadPlayer, respawnedPlayer);

        if (deathEvents.receivedPlayer() != deadPlayer) {
            LocalPlayer received = deathEvents.receivedPlayer();
            throw new AssertionError("death marker must use the old player at minecraft:overworld@104.25,-37.75; got "
                    + (received == null ? "no player" : received.dimension() + "@" + received.x() + "," + received.z()));
        }
        if (!"minecraft:overworld".equals(deathEvents.receivedDimension())
                || deathEvents.receivedX() != 104.25 || deathEvents.receivedZ() != -37.75) {
            throw new AssertionError("death marker must preserve the old player's dimension and coordinates");
        }

        LocalPlayer livingPlayer = new LocalPlayer(false, "minecraft:overworld", 4.0, 6.0);
        LocalPlayer dimensionTransfer = new LocalPlayer(false, "minecraft:the_nether", 2.0, 3.0);
        ClientEvents transitionEvents = invokeClone(livingPlayer, dimensionTransfer);
        if (transitionEvents.deathCount() != 0) {
            throw new AssertionError("a living player's dimension transition must not create a death marker");
        }

        System.out.println("NeoForgeClientRespawnTest passed");
    }

    private static ClientEvents invokeClone(LocalPlayer oldPlayer, LocalPlayer newPlayer) throws Exception {
        ClientEvents events = new ClientEvents();
        Class<?> clientType = Class.forName(CLIENT_CLASS);
        Object client = allocateWithoutConstructor(clientType);
        Field eventsField = clientType.getDeclaredField("events");
        eventsField.setAccessible(true);
        eventsField.set(client, events);

        Class<?> cloneType = Class.forName(CLONE_CLASS);
        Constructor<?> cloneConstructor = findCloneConstructor(cloneType, oldPlayer, newPlayer);
        Object cloneEvent = cloneConstructor.newInstance(null, oldPlayer, newPlayer, null);
        Method handler = clientType.getMethod("onPlayerRespawn", cloneType);
        handler.invoke(client, cloneEvent);
        return events;
    }

    private static Constructor<?> findCloneConstructor(Class<?> cloneType, LocalPlayer oldPlayer,
                                                       LocalPlayer newPlayer) {
        for (Constructor<?> constructor : cloneType.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 4 && parameters[1].isInstance(oldPlayer)
                    && parameters[2].isInstance(newPlayer)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new AssertionError("could not find NeoForge's native four-argument Clone constructor");
    }

    private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }
}
