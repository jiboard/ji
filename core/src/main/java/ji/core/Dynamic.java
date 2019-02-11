package ji.core;

import fj.function.Try1;
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
    static <A> A instance(DynamicType.Unloaded<A> unloaded, ClassLoader loader) throws Exception {
        return instance(unloaded, loader, c -> c.getDeclaredConstructor().newInstance());
    }

    static <A> A instance(DynamicType.Unloaded<A> unloaded, ClassLoader loader, Try1<Class<? extends A>, A, Exception> f) throws Exception {
        return f.f(load(unloaded, loader));
    }

    @SuppressWarnings("unchecked")
    private static <A> Class<? extends A> load(DynamicType.Unloaded<A> unloaded, ClassLoader loader) {
        try {
            return (Class<? extends A>) loader.loadClass(unloaded.getTypeDescription().getName());
        } catch (ClassNotFoundException e) {
            return unloaded.load(loader, INJECTION).getLoaded();
        }
    }

    private Dynamic() {}
}
