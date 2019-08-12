/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vividus.http;

import java.net.URI;
import java.util.List;

public interface IHttpRedirectsProvider
{
    /**
     * Executes HEAD request to get redirects.
     * Throws IllegalStateException in case of status code outside of "200-207"
     * @param from URI to issue HEAD request
     * @return List of redirects. {@code null} if there are no redirects.
     */
    List<URI> getRedirects(URI from);
}
