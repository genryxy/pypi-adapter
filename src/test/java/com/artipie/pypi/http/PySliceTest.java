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
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.headers.Header;
import com.artipie.http.hm.IsHeader;
import com.artipie.http.hm.IsString;
import com.artipie.http.hm.ResponseMatcher;
import com.artipie.http.hm.RsHasBody;
import com.artipie.http.hm.RsHasHeaders;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rs.RsStatus;
import io.reactivex.Flowable;
import java.util.Collections;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link PySlice}.
 * @since 0.6
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.TooManyMethods"})
class PySliceTest {

    /**
     * Test slice.
     */
    private PySlice slice;

    /**
     * Test storage.
     */
    private Storage storage;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
        this.slice = new PySlice(this.storage);
    }

    @Test
    void returnsIndexPage() {
        final byte[] content = "python package".getBytes();
        final String key = "simple/simple-0.1-py3-cp33m-linux_x86.whl";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", "/simple").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            Matchers.allOf(
                new RsHasBody(
                    new IsString(new StringContains("simple-0.1-py3-cp33m-linux_x86.whl"))
                ),
                new RsHasStatus(RsStatus.OK),
                new RsHasHeaders(
                    new Header("Content-type", "text/html"),
                    new Header("Content-Length", "217")
                )
            )
        );
    }

    @Test
    void returnsIndexPageByRootRequest() {
        final byte[] content = "python package".getBytes();
        final String key = "simple/alarmtime-0.1.5.tar.gz";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", "/").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            Matchers.allOf(
                new RsHasBody(
                    new IsString(new StringContains("alarmtime-0.1.5.tar.gz"))
                ),
                new RsHasStatus(RsStatus.OK),
                new RsHasHeaders(
                    new Header("Content-type", "text/html"),
                    new Header("Content-Length", "193")
                )
            )
        );
    }

    @Test
    void redirectsToNormalizedPath() {
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", "/one/Two_three").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.MOVED_PERMANENTLY,
                new IsHeader("Location", "/one/two-three")
            )
        );
    }

    @Test
    void returnsBadRequestOnEmptyPost() {
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("POST", "/sample").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new RsHasStatus(RsStatus.BAD_REQUEST)
        );
    }

    @Test
    void downloadsZip() {
        final byte[] content = "python zip package".getBytes();
        final String key = "my/zip/my-project.zip";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

    @Test
    void downloadsTar() {
        final byte[] content = "python tar package".getBytes();
        final String key = "my-tar/my-project.tar";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

    @Test
    void downloadsTarGz() {
        final byte[] content = "python package".getBytes();
        final String key = "my/my-project.tar.gz";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

    @Test
    void downloadsTarZ() {
        final byte[] content = "python tar z package".getBytes();
        final String key = "my/my-project-z.tar.Z";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

    @Test
    void downloadsTarBz() {
        final byte[] content = "python tar bz2 package".getBytes();
        final String key = "new/my/my-project.tar.bz2";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

    @Test
    void downloadsWheel() {
        final byte[] content = "python wheel".getBytes();
        final String key = "my/my-project.whl";
        this.storage.save(new Key.From(key), new Content.From(content)).join();
        MatcherAssert.assertThat(
            this.slice.response(
                new RequestLine("GET", key).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                content
            )
        );
    }

}
