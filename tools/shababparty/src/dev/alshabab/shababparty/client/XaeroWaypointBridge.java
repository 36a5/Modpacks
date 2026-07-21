package dev.alshabab.shababparty.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Drives a single live waypoint on Xaero's Minimap so the tracked player shows up on the minimap and
 * world map, moving in real time.
 *
 * <h2>Why reflection</h2>
 * Xaero is an optional client mod and its waypoint classes are not part of this mod's compile
 * classpath (the build compiles only against the server's own jars). Everything here is reached by
 * reflection so the class links with or without Xaero present. If Xaero is missing, or its internals
 * have moved in a future version, every call becomes a silent no-op and the ESP overlay carries the
 * feature on its own -- the tracker never sees a crash, only a missing minimap marker.
 *
 * <h2>What it does</h2>
 * It navigates to the player's current waypoint set:
 * <pre>
 *   BuiltInHudModules.MINIMAP.getCurrentSession()
 *       .getWorldManager().getCurrentWorld().getCurrentSet().getList()
 * </pre>
 * adds one {@code temporary} Waypoint (temporary = never written to disk, so it leaves no trace and
 * vanishes on its own), and moves that same waypoint each update. Because the set changes when the
 * player crosses dimensions, {@link #update} re-homes the waypoint into whatever set is current.
 */
final class XaeroWaypointBridge {

    /** Xaero's colour index for the marker. 0 is always a valid palette slot. */
    private static final int COLOR = 0;

    private static boolean disabled;
    private static boolean initialised;

    // Cached reflective handles, resolved once on first use.
    private static Field minimapField;      // BuiltInHudModules.MINIMAP
    private static Method getCurrentSession;
    private static Method getWorldManager;
    private static Method getCurrentWorld;
    private static Method getCurrentSet;
    private static Method getList;
    private static Constructor<?> waypointCtor;
    private static Method setX;
    private static Method setY;
    private static Method setZ;
    private static Method setName;

    // The one waypoint we own, and the list it currently lives in.
    private static Object waypoint;
    private static List<Object> ownerList;

    private XaeroWaypointBridge() {
    }

    private static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            final Class<?> builtIn = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            minimapField = builtIn.getField("MINIMAP");

            final Class<?> hudModule = Class.forName("xaero.hud.module.HudModule");
            getCurrentSession = hudModule.getMethod("getCurrentSession");

            final Class<?> session = Class.forName("xaero.hud.minimap.module.MinimapSession");
            getWorldManager = session.getMethod("getWorldManager");

            final Class<?> worldManager = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            getCurrentWorld = worldManager.getMethod("getCurrentWorld");

            final Class<?> waypointWorld = Class.forName("xaero.common.minimap.waypoints.WaypointWorld");
            getCurrentSet = waypointWorld.getMethod("getCurrentSet");

            final Class<?> waypointSet = Class.forName("xaero.common.minimap.waypoints.WaypointSet");
            getList = waypointSet.getMethod("getList");

            final Class<?> waypoint = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            waypointCtor = waypoint.getConstructor(
                    int.class, int.class, int.class, String.class, String.class, int.class);
            setX = waypoint.getMethod("setX", int.class);
            setY = waypoint.getMethod("setY", int.class);
            setZ = waypoint.getMethod("setZ", int.class);
            setName = waypoint.getMethod("setName", String.class);
            waypoint.getMethod("setTemporary", boolean.class); // presence check only
        } catch (final Throwable t) {
            // Xaero absent or its API moved. Give up quietly and stay a no-op for the session.
            disabled = true;
        }
    }

    /** Move (or create) the marker to a live position in the tracker's current dimension. */
    @SuppressWarnings("unchecked")
    static void update(final String name, final int x, final int y, final int z) {
        init();
        if (disabled) {
            return;
        }
        try {
            final Object module = minimapField.get(null);
            if (module == null) {
                return;
            }
            final Object session = getCurrentSession.invoke(module);
            if (session == null) {
                return; // No world loaded yet.
            }
            final Object worldManager = getWorldManager.invoke(session);
            final Object world = getCurrentWorld.invoke(worldManager);
            if (world == null) {
                return;
            }
            final Object set = getCurrentSet.invoke(world);
            if (set == null) {
                return;
            }
            final List<Object> list = (List<Object>) getList.invoke(set);
            if (list == null) {
                return;
            }

            if (waypoint == null) {
                waypoint = waypointCtor.newInstance(x, y, z, name, initial(name), COLOR);
                waypoint.getClass().getMethod("setTemporary", boolean.class).invoke(waypoint, true);
            }
            // The set changes when the tracker crosses dimensions; follow it there.
            if (ownerList != list) {
                if (ownerList != null) {
                    ownerList.remove(waypoint);
                }
                if (!list.contains(waypoint)) {
                    list.add(waypoint);
                }
                ownerList = list;
            }

            setX.invoke(waypoint, x);
            setY.invoke(waypoint, y);
            setZ.invoke(waypoint, z);
            setName.invoke(waypoint, name);
        } catch (final Throwable t) {
            disabled = true; // Anything unexpected: stop touching Xaero for the rest of the session.
        }
    }

    /** Remove the marker. Safe to call when nothing was ever added. */
    static void clear() {
        if (disabled || waypoint == null) {
            waypoint = null;
            ownerList = null;
            return;
        }
        try {
            if (ownerList != null) {
                ownerList.remove(waypoint);
            }
        } catch (final Throwable t) {
            disabled = true;
        } finally {
            waypoint = null;
            ownerList = null;
        }
    }

    private static String initial(final String name) {
        return name == null || name.isEmpty()
                ? "?"
                : name.substring(0, 1).toUpperCase();
    }
}
