/*
 * Copyright 2019 Zhong Lunfu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ji.core;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@AutoService(ClassLoaderInjection.Target.class)
public final class Dispatcher {
    private final static AtomicReference<Map<String, Handler>> ref = new AtomicReference<>(Collections.emptyMap());

    static void register(String name, Handler handler) {
        registerAll(Collections.singletonMap(name, handler));
    }

    static void registerAll(Map<String, Handler> handlers) {
        for (; ; ) {
            final Map<String, Handler> cur = ref.get();
            if (ref.compareAndSet(cur, update(cur, handlers))) break;
        }
    }

    private static Map<String, Handler> update(Map<String, Handler> cur, Map<String, Handler> handlers) {
        final Map<String, Handler> u = new HashMap<>(cur);
        u.putAll(handlers);
        return Collections.unmodifiableMap(u);
    }

    public static Object execute(String name, Object... args) {
        final Handler handler = ref.get().get(name);
        if (handler == null) {
            return DefaultHandler.INSTANCE.execute(args);
        } else {
            return handler.execute(args);
        }
    }

    @AutoService(ClassLoaderInjection.Target.class)
    public interface Handler {
        Object execute(Object... args);
    }

    @AutoService(ClassLoaderInjection.Target.class)
    @VisibleForTesting
    static class DefaultHandler implements Handler {

        private final static DefaultHandler INSTANCE = new DefaultHandler();

        private DefaultHandler() {}

        @Override
        public Object execute(Object... args) {
            return null;
        }
    }

    private Dispatcher() {}
}
