/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.pypi.http;

import com.artipie.asto.Content;
import com.artipie.asto.Copy;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.fs.FileStorage;
import com.artipie.http.Headers;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rs.RsFull;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.pypi.NormalizedProjectName;
import com.artipie.pypi.meta.Metadata;
import com.artipie.pypi.meta.PackageInfo;
import com.jcabi.xml.XMLDocument;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.io.FileUtils;
import org.cactoos.list.ListOf;
import org.cactoos.scalar.Unchecked;
import org.reactivestreams.Publisher;

/**
 * Search slice.
 * @since 0.7
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.UnusedPrivateMethod"})
public final class SearchSlice implements Slice {

    /**
     * Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage Storage
     */
    public SearchSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        final Path temp = new Unchecked<>(() -> Files.createTempDirectory("py-artifact-")).value();
        return new AsyncResponse(
            new NameFromXml(body).get().thenCompose(
                name -> {
                    final Key.From key = new Key.From(
                        new NormalizedProjectName.Simple(name).value()
                    );
                    return this.storage.list(key).thenCompose(
                        list -> {
                            CompletableFuture<Content> res = new CompletableFuture<>();
                            if (list.isEmpty()) {
                                res.complete(new Content.From(SearchSlice.empty()));
                            } else {
                                final Key latest = list.stream().map(Key::string)
                                    .max(Comparator.naturalOrder())
                                    .map(Key.From::new)
                                    .orElseThrow(IllegalStateException::new);
                                res = this.tempArtifact(new Key.From(latest), temp)
                                    .thenApply(path -> new Metadata.FromArchive(path).read())
                                    .thenApply(info -> new Content.From(SearchSlice.found(info)));
                            }
                            return res;
                        }
                    );
                }
            ).handle(
                (content, throwable) -> {
                    final Response res;
                    if (throwable == null) {
                        res = new RsFull(
                            RsStatus.OK, new Headers.From("content-type", "text/xml"), content
                        );
                    } else {
                        res = new RsWithStatus(RsStatus.INTERNAL_ERROR);
                    }
                    FileUtils.deleteQuietly(temp.toFile());
                    return res;
                }
            )
        );
    }

    /**
     * Response body when no packages found by given name.
     * @return Xml string
     */
    static byte[] empty() {
        return String.join(
            "\n", "<methodResponse>",
            "<params>",
            "<param>",
            "<value><array><data>",
            "</data></array></value>",
            "</param>",
            "</params>",
            "</methodResponse>"
        ).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Response body xml for search result.
     * @param info Package info
     * @return Xml string
     */
    static byte[] found(final PackageInfo info) {
        return String.join(
            "\n",
            "<?xml version='1.0'?>",
            "<methodResponse>",
            "<params>",
            "<param>",
            "<value><array><data>",
            "<value><struct>",
            "<member>",
            "<name>name</name>",
            String.format("<value><string>%s</string></value>", info.name()),
            "</member>",
            "<member>",
            "<name>summary</name>",
            String.format("<value><string>%s</string></value>", info.summary()),
            "</member>",
            "<member>",
            "<name>version</name>",
            String.format("<value><string>%s</string></value>", info.version()),
            "</member>",
            "<member>",
            "<name>_pypi_ordering</name>",
            "<value><boolean>0</boolean></value>",
            "</member>",
            "</struct></value>",
            "</data></array></value>",
            "</param>",
            "</params>",
            "</methodResponse>"
        ).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Copy artifact to the temp storage.
     * @param artifact Artifact key to copy
     * @param temp Temp dir
     * @return Path of the temp file
     */
    private CompletableFuture<Path> tempArtifact(final Key artifact, final Path temp) {
        return new Copy(this.storage, new ListOf<>(artifact)).copy(new FileStorage(temp))
            .thenApply(nothing -> temp.resolve(artifact.string()));
    }

    /**
     * Python project name from request body xml.
     * @since 0.7
     */
    static final class NameFromXml {

        /**
         * Xml body.
         */
        private final Publisher<ByteBuffer> body;

        /**
         * Ctor.
         * @param body Body
         */
        NameFromXml(final Publisher<ByteBuffer> body) {
            this.body = body;
        }

        /**
         * Obtain project name to from xml.
         * @return Name of the project
         */
        CompletionStage<String> get() {
            final String query = "//member/value/array/data/value/string/text()";
            return new PublisherAs(this.body).string(StandardCharsets.UTF_8).thenApply(
                xml -> new XMLDocument(xml)
                    // @checkstyle LineLengthCheck (1 line)
                    .nodes("/*[local-name()='methodCall']/*[local-name()='params']/*[local-name()='param']/*[local-name()='value']/*[local-name()='struct']/*[local-name()='member']")
            ).thenApply(
                nodes -> nodes.stream()
                    .filter(
                        node -> node.xpath("//member/name/text()").get(0).equals("name")
                        && !node.xpath(query).isEmpty()
                    )
                    .findFirst()
                    .map(node -> node.xpath(query))
                    .map(node -> node.get(0))
                    .orElseThrow(
                        () -> new IllegalArgumentException("Invalid xml, project name not found")
                    )
            );
        }
    }
}
