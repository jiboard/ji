package ji.core;

import net.bytebuddy.dynamic.DynamicType;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION;

/**
 * Some utils.
 */
final class Dynamic {
    /**
     * A reusable method for new a instance of {@link DynamicType.Unloaded}.
     *
     * @param unloaded to load and instance.
     * @param loader   which should load with.
     * @param <A>      instance to be return.
     * @return
     * @throws Exception
     */
    public static <A> A instance(DynamicType.Unloaded<A> unloaded, ClassLoader loader) throws Exception {
        return unloaded.load(loader, INJECTION)
                       .getLoaded()
                       .getDeclaredConstructor()
                       .newInstance();
    }

    private Dynamic() {}
}
