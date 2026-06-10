package github.meloweh.antigrieflever.protection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class DestructionContext {
    private static final ThreadLocal<Deque<UUID>> ACTORS = ThreadLocal.withInitial(ArrayDeque::new);

    private DestructionContext() {
    }

    @Nullable
    public static UUID currentActor() {
        return ACTORS.get().peek();
    }

    public static <T> T callWithActor(@Nullable UUID actor, Supplier<T> action) {
        if (actor == null) {
            return action.get();
        }

        Deque<UUID> actors = ACTORS.get();
        actors.push(actor);
        try {
            return action.get();
        } finally {
            actors.pop();
            if (actors.isEmpty()) {
                ACTORS.remove();
            }
        }
    }

    public static void runWithActor(@Nullable UUID actor, Runnable action) {
        callWithActor(actor, () -> {
            action.run();
            return null;
        });
    }
}
