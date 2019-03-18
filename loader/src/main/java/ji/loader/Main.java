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
package ji.loader;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;

public class Main {
    private static final String LIB = "lib/";
    private static final String PREMAIN = "premain";
    private static final String JI_CORE_MAIN = "ji.core.Main";

    public static void premain(final String args, final Instrumentation inst) throws Exception {
        final URL[] urls = nestArchiveUrls(new JarFileArchive(getArchiveFileContains(Main.class)));
        CompoundableClassLoader loader = new CompoundableClassLoader(urls);
        try {
            loader.include(Main.class.getClassLoader())
                  .loadClass(JI_CORE_MAIN)
                  .getMethod(PREMAIN, String.class, Instrumentation.class)
                  .invoke(null, args, inst);
        } finally {
            loader.close();
        }
    }

    private static URL[] nestArchiveUrls(Archive archive) throws IOException {
        final List<Archive> archives = archive.getNestedArchives(e -> !e.isDirectory() && e.getName().startsWith(LIB));

        final URL[] urls = new URL[archives.size()];

        for (int i = 0; i < urls.length; i++) {
            urls[i] = archives.get(i).getUrl();
        }

        return urls;
    }

    private static File getArchiveFileContains(Class<?> klass) throws URISyntaxException {
        final ProtectionDomain protectionDomain = klass.getProtectionDomain();
        final CodeSource codeSource = protectionDomain.getCodeSource();
        final URI location = (codeSource == null ? null : codeSource.getLocation().toURI());
        final String path = (location == null ? null : location.getSchemeSpecificPart());
        if (path == null) {
            throw new IllegalStateException("Unable to determine code source archive");
        }
        final File root = new File(path);
        if (!root.exists() || root.isDirectory()) {
            throw new IllegalStateException("Unable to determine code source archive from " + root);
        }
        return root;
    }

}
