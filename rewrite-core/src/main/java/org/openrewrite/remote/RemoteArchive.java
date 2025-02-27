/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.remote;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import org.intellij.lang.annotations.Language;
import org.openrewrite.FileAttributes;
import org.openrewrite.PathUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.ipc.http.HttpSender;
import org.openrewrite.marker.Markers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents a source to be extracted from within an archive hosted at a remote URI.
 * If you want to download and retain the entire archive, use {@link RemoteFile}.
 * Useful when a Recipe wishes to create a SourceFile based on something specific from within a remote archive, but not
 * the entire archive.
 *
 * Downloading and extracting the correct file from within the archive are not handled during Recipe execution.
 * Post-processing of Recipe results by a build plugin or other caller of OpenRewrite is responsible for this.
 */
@Value
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@With
public class RemoteArchive implements Remote {
    @EqualsAndHashCode.Include
    UUID id;

    Path sourcePath;
    Markers markers;
    URI uri;

    @Nullable
    Charset charset;

    boolean charsetBomMarked;

    @Nullable
    FileAttributes fileAttributes;

    @Language("markdown")
    String description;

    Path path;

    @Override
    public InputStream getInputStream(HttpSender httpSender) {
        //noinspection resource
        HttpSender.Response response = httpSender.send(httpSender.get(uri.toString()).build());
        InputStream body = response.getBody();
        return readIntoArchive(body, path.toString());
    }

    private InputStream readIntoArchive(InputStream body, String path) {
        String pathBeforeBang;
        String pathAfterBang = null;
        int bangIndex = path.indexOf('!');
        if(bangIndex == -1) {
            pathBeforeBang = path;
        } else {
            pathBeforeBang = path.substring(0, bangIndex);
            pathAfterBang = path.substring(bangIndex + 1);
        }

        ZipInputStream zis = new ZipInputStream(body);

        try {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (PathUtils.equalIgnoringSeparators(entry.getName(), pathBeforeBang)) {
                    if(pathAfterBang == null) {
                        return new InputStream() {
                            @Override
                            public int read() throws IOException {
                                return zis.read();
                            }

                            @Override
                            public void close() throws IOException {
                                zis.closeEntry();
                                zis.close();
                            }
                        };
                    } else {
                        return readIntoArchive(zis, pathAfterBang);
                    }
                }
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load path " + path + " in zip file " + uri, e);
        }
        throw new IllegalArgumentException("Unable to find path " + path + " in zip file " + uri);
    }
}
