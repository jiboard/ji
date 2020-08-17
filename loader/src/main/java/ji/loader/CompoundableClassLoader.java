/*
 * Copyright 2020 Zhong Lunfu
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
package ji.loader;

import org.springframework.boot.loader.LaunchedURLClassLoader;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

class CompoundableClassLoader extends LaunchedURLClassLoader implements Compoundable {
    private static final ClassLoader BOOTSTRAP_CLASS_LOADER = null;

    private final Set<ClassLoader> externals = new CopyOnWriteArraySet<>();

    CompoundableClassLoader(URL[] urls) {
        super(urls, BOOTSTRAP_CLASS_LOADER);
    }

    @Override
    public ClassLoader include(ClassLoader cl) {
        externals.add(cl);
        return this;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            for (ClassLoader external : externals) {
                try {
                    final Class<?> aClass = external.loadClass(name);
                    if (resolve) resolveClass(aClass);
                    return aClass;
                } catch (ClassNotFoundException ignore) { /* ignore here */}
            }

            throw e;
        }
    }
}
