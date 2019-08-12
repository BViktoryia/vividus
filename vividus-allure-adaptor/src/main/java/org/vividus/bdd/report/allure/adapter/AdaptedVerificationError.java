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

package org.vividus.bdd.report.allure.adapter;

import java.io.PrintWriter;

import org.vividus.softassert.exception.VerificationError;

final class AdaptedVerificationError extends VerificationError
{
    private static final long serialVersionUID = 4813008988699753359L;

    private final VerificationError original;

    AdaptedVerificationError(String message, VerificationError original)
    {
        super(message, original.getErrors());
        this.original = original;
        setStackTrace(original.getStackTrace());
    }

    @Override
    public void printStackTrace(PrintWriter writer)
    {
        writer.println();
        original.printStackTrace(writer);
    }
}
